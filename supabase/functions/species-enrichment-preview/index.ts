type SpeciesRequest = {
  gbifUsageKey?: number;
  scientificName?: string;
  canonicalName?: string;
  commonName?: string | null;
  kingdom?: string | null;
  phylum?: string | null;
  className?: string | null;
  order?: string | null;
  family?: string | null;
  genus?: string | null;
  debugOpenAI?: boolean;
  debugGemini?: boolean;
};

type GbifSpecies = {
  key?: number;
  scientificName?: string;
  canonicalName?: string;
  kingdom?: string;
  phylum?: string;
  class?: string;
  order?: string;
  family?: string;
  genus?: string;
};

type GbifSpeciesMatch = {
  usageKey?: number;
  acceptedUsageKey?: number;
  scientificName?: string;
  canonicalName?: string;
};

type GbifDescription = {
  type?: string;
  description?: string;
  source?: string;
};

type GbifDescriptionsResponse = {
  results?: GbifDescription[];
};

type GbifOccurrence = {
  country?: string;
  stateProvince?: string;
  habitat?: string;
  iucnRedListCategory?: string;
};

type GbifOccurrenceResponse = {
  results?: GbifOccurrence[];
};

type InatTaxon = {
  preferred_common_name?: string;
  wikipedia_url?: string;
  default_photo?: {
    medium_url?: string;
    square_url?: string;
    url?: string;
    attribution?: string;
    attribution_name?: string;
    license_code?: string;
  };
  conservation_status?: {
    status_name?: string;
    status?: string;
    authority?: string;
  };
};

type InatTaxaResponse = {
  results?: InatTaxon[];
};

type WikipediaSummary = {
  extract?: string;
  title?: string;
};

type WikipediaExtractResponse = {
  query?: {
    pages?: Record<string, { extract?: string }>;
  };
};

type IucnAssessment = {
  latest?: boolean;
  red_list_category?: string;
  red_list_category_code?: string;
  category?: string;
  category_code?: string;
  year_published?: number;
  assessment_id?: number;
};

type IucnScientificNameResponse = {
  assessments?: IucnAssessment[];
  result?: IucnAssessment[];
  results?: IucnAssessment[];
};

type EnrichmentField = {
  value: string | null;
  sourceUrl?: string | null;
};

type OpenAIEnrichmentResponse = {
  habitat?: EnrichmentField;
  diet?: EnrichmentField;
  lifespan?: EnrichmentField;
  distribution?: EnrichmentField;
  conservationStatus?: EnrichmentField;
};

type OpenAIResponsesApiResponse = {
  output_text?: string;
  output?: Array<{
    content?: Array<{
      text?: string;
    }>;
  }>;
};

type OpenAIFallbackResult = {
  enrichment: OpenAIEnrichmentResponse | null;
  debug: {
    attempted: boolean;
    hasApiKey: boolean;
    missingFields: string[];
    httpStatus?: number;
    parseOk?: boolean;
    acceptedFields?: string[];
    acceptedSourceUrls?: string[];
  };
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const jsonHeaders = {
  ...corsHeaders,
  "Content-Type": "application/json",
};

const notEnriched = null;

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (request.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }

  const body = (await request.json().catch(() => ({}))) as SpeciesRequest;
  const requestedGbifUsageKey = Number(body.gbifUsageKey ?? 0);
  const canonicalName = clean(body.canonicalName) ?? clean(body.scientificName);

  if (!requestedGbifUsageKey || !canonicalName) {
    return jsonResponse({ error: "gbifUsageKey and canonicalName are required" }, 400);
  }

  const [gbifResolution, inatTaxon] = await Promise.all([
    resolveGbifSpecies(requestedGbifUsageKey, canonicalName),
    fetchInatTaxon(canonicalName),
  ]);
  const [descriptions, occurrences] = gbifResolution.key ? await Promise.all([
    fetchJson<GbifDescriptionsResponse>(`https://api.gbif.org/v1/species/${gbifResolution.key}/descriptions`),
    fetchJson<GbifOccurrenceResponse>(`https://api.gbif.org/v1/occurrence/search?taxon_key=${gbifResolution.key}&limit=20`),
  ]) : [null, null];
  const gbifSpecies = gbifResolution.species;

  const wikipediaTitleValue = wikipediaTitle(inatTaxon?.wikipedia_url, canonicalName);
  const [wikipediaSummary, wikipediaExtract, iucnAssessment] = await Promise.all([
    fetchWikipediaSummary(wikipediaTitleValue),
    fetchWikipediaExtract(wikipediaTitleValue),
    fetchIucnAssessment(canonicalName),
  ]);
  const taxonomy = taxonomyLine(gbifSpecies, body);
  let distribution = distributionFrom(descriptions, occurrences, wikipediaSummary);
  let habitat = habitatFrom(occurrences, wikipediaExtract ?? wikipediaSummary?.extract);
  let diet = dietFrom(wikipediaExtract ?? wikipediaSummary?.extract);
  let lifespan = lifespanFrom(wikipediaExtract ?? wikipediaSummary?.extract);
  let conservationStatus = conservationFrom(iucnAssessment, inatTaxon, occurrences);
  const photo = photoFrom(inatTaxon);
  const openAiFallback = await fetchOpenAIFallback({
    canonicalName,
    commonName: clean(inatTaxon?.preferred_common_name) ?? clean(body.commonName),
    scientificName: clean(gbifSpecies?.scientificName) ?? clean(body.scientificName) ?? canonicalName,
    taxonomy,
    habitat,
    diet,
    lifespan,
    distribution,
    conservationStatus,
  });
  const openAiEnrichment = openAiFallback?.enrichment ?? null;
  habitat = habitat ?? trustedOpenAIValue(openAiEnrichment?.habitat);
  diet = diet ?? trustedOpenAIValue(openAiEnrichment?.diet);
  lifespan = lifespan ?? trustedOpenAIValue(openAiEnrichment?.lifespan);
  distribution = distribution ?? trustedOpenAIValue(openAiEnrichment?.distribution);
  conservationStatus = conservationStatus ?? trustedOpenAIValue(openAiEnrichment?.conservationStatus);

  return jsonResponse({
    commonName: clean(inatTaxon?.preferred_common_name) ?? clean(body.commonName) ?? canonicalName,
    scientificName: clean(gbifSpecies?.scientificName) ?? clean(body.scientificName) ?? canonicalName,
    taxonomy,
    habitat,
    diet,
    lifespan,
    distribution,
    conservationStatus,
    sourceApi: sourceApi(inatTaxon, wikipediaSummary, wikipediaExtract, iucnAssessment, openAiEnrichment),
    lastEnrichedDate: todayLabel(),
    photoUrl: photo?.url ?? notEnriched,
    photoAttribution: photo?.attribution ?? notEnriched,
    photoLicense: photo?.license ?? notEnriched,
    photoSource: photo?.source ?? notEnriched,
    ...(body.debugOpenAI ? { openAiDebug: openAiFallback?.debug ?? null } : {}),
    ...(body.debugGemini ? { geminiDebug: openAiFallback?.debug ?? null } : {}),
  });
});

async function resolveGbifSpecies(requestedKey: number, canonicalName: string) {
  const directSpecies = await fetchJson<GbifSpecies>(`https://api.gbif.org/v1/species/${requestedKey}`);
  if (matchesSpeciesName(directSpecies, canonicalName)) {
    return { key: requestedKey, species: directSpecies };
  }

  const matchUrl = new URL("https://api.gbif.org/v1/species/match");
  matchUrl.searchParams.set("name", canonicalName);
  matchUrl.searchParams.set("rank", "SPECIES");
  const match = await fetchJson<GbifSpeciesMatch>(matchUrl.toString());
  const matchedKey = match?.acceptedUsageKey ?? match?.usageKey ?? 0;
  if (!matchedKey) return { key: 0, species: null };

  const matchedSpecies = await fetchJson<GbifSpecies>(`https://api.gbif.org/v1/species/${matchedKey}`);
  return matchesSpeciesName(matchedSpecies, canonicalName)
    ? { key: matchedKey, species: matchedSpecies }
    : { key: 0, species: null };
}

async function fetchInatTaxon(canonicalName: string) {
  const url = new URL("https://api.inaturalist.org/v1/taxa");
  url.searchParams.set("q", canonicalName);
  url.searchParams.set("per_page", "1");
  const response = await fetchJson<InatTaxaResponse>(url.toString());
  return response?.results?.[0] ?? null;
}

function wikipediaTitle(wikipediaUrl: string | undefined, canonicalName: string) {
  return wikipediaUrl?.split("/wiki/")[1] ?? canonicalName.replaceAll(" ", "_");
}

async function fetchWikipediaSummary(title: string) {
  if (!title) return null;
  return fetchJson<WikipediaSummary>(`https://en.wikipedia.org/api/rest_v1/page/summary/${title}`);
}

async function fetchWikipediaExtract(title: string) {
  if (!title) return null;

  const url = new URL("https://en.wikipedia.org/w/api.php");
  url.searchParams.set("action", "query");
  url.searchParams.set("prop", "extracts");
  url.searchParams.set("explaintext", "1");
  url.searchParams.set("redirects", "1");
  url.searchParams.set("format", "json");
  url.searchParams.set("titles", safeDecode(title).replaceAll("_", " "));

  const response = await fetchJson<WikipediaExtractResponse>(url.toString());
  const pages = response?.query?.pages;
  if (!pages) return null;

  return Object.values(pages)
    .map((page) => clean(page.extract))
    .find(Boolean) ?? null;
}

async function fetchIucnAssessment(canonicalName: string) {
  const apiKey = Deno.env.get("IUCN_REDLIST_KEY");
  if (!apiKey) return null;

  const [genusName, speciesName] = canonicalName.split(/\s+/);
  if (!genusName || !speciesName) return null;

  const url = new URL("https://api.iucnredlist.org/api/v4/taxa/scientific_name");
  url.searchParams.set("genus_name", genusName);
  url.searchParams.set("species_name", speciesName);

  const response = await fetchJson<IucnScientificNameResponse>(url.toString(), {
    Authorization: apiKey,
  });
  const assessments = response?.assessments ?? response?.result ?? response?.results ?? [];
  return assessments.find((assessment) => assessment.latest) ?? assessments[0] ?? null;
}

async function fetchOpenAIFallback(input: {
  canonicalName: string;
  commonName: string | null;
  scientificName: string;
  taxonomy: string | null;
  habitat: string | null;
  diet: string | null;
  lifespan: string | null;
  distribution: string | null;
  conservationStatus: string | null;
}) {
  const missingFields = [
    input.habitat ? null : "habitat",
    input.diet ? null : "diet",
    input.lifespan ? null : "lifespan",
    input.distribution ? null : "distribution",
    input.conservationStatus ? null : "conservationStatus",
  ].filter(Boolean);
  const apiKey = Deno.env.get("OPENAI_API_KEY");
  const baseDebug = {
    attempted: Boolean(apiKey && missingFields.length > 0),
    hasApiKey: Boolean(apiKey),
    missingFields: missingFields as string[],
  };
  if (!apiKey || missingFields.length === 0) return { enrichment: null, debug: baseDebug };

  const openAiResponse = await fetch(
    "https://api.openai.com/v1/responses",
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        Authorization: `Bearer ${apiKey}`,
        "User-Agent": "BioMemo/1.0 species-enrichment-preview (Supabase Edge Function)",
      },
      body: JSON.stringify({
        model: Deno.env.get("OPENAI_ENRICHMENT_MODEL") ?? "gpt-4.1-mini",
        input: [{
          role: "user",
          content: [{ type: "input_text", text: openAiPrompt(input, missingFields as string[]) }],
        }],
        tools: [{ type: "web_search_preview", search_context_size: "medium" }],
        text: {
          format: {
            type: "json_schema",
            name: "species_enrichment",
            strict: true,
            schema: openAiResponseSchema(),
          },
        },
        max_output_tokens: 1200,
      }),
    },
  ).catch((error) => {
    console.error("OpenAI species enrichment request failed", error);
    return null;
  });
  if (!openAiResponse?.ok) {
    const errorBody = await openAiResponse?.text().catch(() => "");
    console.error("OpenAI species enrichment returned non-OK status", openAiResponse?.status, errorBody);
    return {
      enrichment: null,
      debug: {
        ...baseDebug,
        httpStatus: openAiResponse?.status,
      },
    };
  }

  const response = (await openAiResponse.json().catch(() => null)) as OpenAIResponsesApiResponse | null;
  const text = response?.output_text ?? response?.output?.flatMap((item) => item.content ?? [])
    .map((content) => content.text)
    .find((value) => typeof value === "string");
  if (!text) {
    return {
      enrichment: null,
      debug: {
        ...baseDebug,
        httpStatus: openAiResponse.status,
        parseOk: false,
      },
    };
  }

  const enrichment = parseOpenAIResponse(text);
  return {
    enrichment,
    debug: {
      ...baseDebug,
      httpStatus: openAiResponse.status,
      parseOk: Boolean(enrichment),
      acceptedFields: acceptedOpenAIFields(enrichment),
      acceptedSourceUrls: acceptedOpenAISourceUrls(enrichment),
    },
  };
}

async function fetchJson<T>(
  url: string,
  headers: Record<string, string> = {},
  init: RequestInit = {},
): Promise<T | null> {
  const response = await fetch(url, {
    ...init,
    headers: {
      Accept: "application/json",
      "User-Agent": "BioMemo/1.0 species-enrichment-preview (Supabase Edge Function)",
      ...headers,
    },
  }).catch(() => null);

  if (!response?.ok) return null;
  return (await response.json().catch(() => null)) as T | null;
}

function taxonomyLine(gbif: GbifSpecies | null, fallback: SpeciesRequest) {
  return [
    gbif?.kingdom ?? fallback.kingdom,
    gbif?.phylum ?? fallback.phylum,
    gbif?.class ?? fallback.className,
    gbif?.order ?? fallback.order,
    gbif?.family ?? fallback.family,
    gbif?.genus ?? fallback.genus,
  ]
    .map(clean)
    .filter(Boolean)
    .join(" · ") || notEnriched;
}

function distributionFrom(
  descriptions: GbifDescriptionsResponse | null,
  occurrences: GbifOccurrenceResponse | null,
  wikipedia: WikipediaSummary | null,
) {
  const distributionDescription = descriptions?.results
    ?.find((row) => row.type?.toLowerCase() === "distribution" && clean(row.description))
    ?.description;
  if (clean(distributionDescription)) return clean(distributionDescription);

  const places = [...new Set((occurrences?.results ?? []).map((row) => clean(row.stateProvince) ?? clean(row.country)).filter(Boolean))];
  if (places.length > 0) return places.slice(0, 5).join(", ");

  return endemicPhrase(wikipedia?.extract) ?? notEnriched;
}

function habitatFrom(occurrences: GbifOccurrenceResponse | null, wikipediaExtract: string | null | undefined) {
  const habitat = occurrences?.results?.map((row) => clean(row.habitat)).find(isUsefulValue);
  if (habitat) return habitat;

  const extract = wikipediaExtract?.toLowerCase() ?? "";
  if (extract.includes("savannah") || extract.includes("savanna")) return "Savannahs and woodlands";
  if (extract.includes("woodland")) return "Woodlands";
  if (extract.includes("desert")) return "Desert environments";
  if (extract.includes("forest")) return "Forests";
  if (extract.includes("jungle")) return "Jungle";
  return notEnriched;
}

function dietFrom(extract: string | null | undefined) {
  const text = clean(extract);
  if (!text) return notEnriched;

  const section = sectionText(text, "Diet");
  const dietSentences = splitSentences(section).filter(isUsefulDietSentence);
  const sectionDietSentence = dietSentences.find((sentence) =>
    /\b(herbivores?|carnivores?|omnivores?|insectivores?|frugivores?|feeding on|feed on|feeds? on|eating|eats|food source is|browse on)\b/i.test(sentence)
  );
  if (sectionDietSentence) return truncate(cleanSentence(sectionDietSentence), 260);

  const allSentences = splitSentences(text);
  const broadPreySentence = allSentences.find((sentence) =>
    isUsefulDietSentence(sentence) &&
    /\b(wide range of prey|prey which includes|prey includes|primary prey|food source is|browse on|feed on)\b/i.test(sentence)
  );
  if (broadPreySentence) return truncate(cleanSentence(broadPreySentence), 260);

  const dietSentence = dietSentences.find((sentence) =>
    /\b(preys? on|feeds? (mainly|primarily|mostly)|diet consists)\b/i.test(sentence)
  );
  if (dietSentence) return truncate(cleanSentence(dietSentence), 260);

  if (/\bparasitic plant\b/i.test(text)) return "Parasitic plant";

  const firstDietSentence = dietSentences.find(Boolean);
  return firstDietSentence ? truncate(cleanSentence(firstDietSentence), 260) : notEnriched;
}

function lifespanFrom(extract: string | null | undefined) {
  const text = clean(extract);
  if (!text) return notEnriched;

  const lifespanSection = sectionText(text, "Lifespan");
  const sentences = splitSentences(`${lifespanSection} ${text}`);
  const lifespanSentence = sentences.find((sentence) =>
    /\b(life expectancy|lifespan|lives? for|can live)\b/i.test(sentence) && /\b\d{1,3}\b/.test(sentence)
  );

  return lifespanSentence ? truncate(cleanSentence(lifespanSentence), 220) : notEnriched;
}

function conservationFrom(iucn: IucnAssessment | null, inat: InatTaxon | null, occurrences: GbifOccurrenceResponse | null) {
  const iucnStatus = iucnStatusFrom(iucn);
  if (iucnStatus) return iucnStatus;

  const statusName = clean(inat?.conservation_status?.status_name);
  if (statusName) return statusName.toLowerCase();

  const occurrenceStatus = occurrences?.results?.map((row) => clean(row.iucnRedListCategory)).find(Boolean);
  return occurrenceStatus ? iucnCodeToLabel(occurrenceStatus) : notEnriched;
}

function photoFrom(inat: InatTaxon | null) {
  const photo = inat?.default_photo;
  const license = clean(photo?.license_code);
  if (!photo || !isAllowedPhotoLicense(license)) return null;

  const url = clean(photo.medium_url) ?? clean(photo.url) ?? clean(photo.square_url);
  if (!url) return null;

  return {
    url,
    attribution: clean(photo.attribution_name) ?? clean(photo.attribution),
    license,
    source: "iNaturalist",
  };
}

function sourceApi(
  inat: InatTaxon | null,
  wikipedia: WikipediaSummary | null,
  wikipediaExtract: string | null,
  iucn: IucnAssessment | null,
  openAi: OpenAIEnrichmentResponse | null,
) {
  return ["GBIF", inat ? "iNaturalist" : null, iucn ? "IUCN Red List" : null, wikipedia || wikipediaExtract ? "Wikipedia" : null, hasTrustedOpenAIValue(openAi) ? "OpenAI Search" : null]
    .filter(Boolean)
    .join(", ");
}

function endemicPhrase(extract: string | undefined) {
  const match = extract?.match(/endemic to ([^.]+)/i);
  return match?.[0] ?? null;
}

function iucnCodeToLabel(code: string) {
  const normalized = code.toUpperCase();
  const labels: Record<string, string> = {
    LC: "least concern",
    NT: "near threatened",
    VU: "vulnerable",
    EN: "endangered",
    CR: "critically endangered",
    EW: "extinct in the wild",
    EX: "extinct",
  };
  return labels[normalized] ?? normalized.toLowerCase();
}

function iucnStatusFrom(assessment: IucnAssessment | null) {
  const label = clean(assessment?.red_list_category) ?? clean(assessment?.category);
  if (label) return label.toLowerCase();

  const code = clean(assessment?.red_list_category_code) ?? clean(assessment?.category_code);
  return code ? iucnCodeToLabel(code) : null;
}

function isAllowedPhotoLicense(license: string | null | undefined) {
  const normalized = license?.toLowerCase();
  return Boolean(normalized && normalized !== "all-rights-reserved");
}

function isUsefulDietSentence(sentence: string) {
  return !/\b(newborn|juvenile|young)\b/i.test(sentence);
}

function trustedOpenAIValue(field: EnrichmentField | undefined) {
  if (!clean(field?.value)) return null;
  return clean(field?.value);
}

function hasTrustedOpenAIValue(response: OpenAIEnrichmentResponse | null) {
  return Boolean(
    trustedOpenAIValue(response?.habitat) ||
    trustedOpenAIValue(response?.diet) ||
    trustedOpenAIValue(response?.lifespan) ||
    trustedOpenAIValue(response?.distribution) ||
    trustedOpenAIValue(response?.conservationStatus)
  );
}

function parseOpenAIResponse(text: string) {
  try {
    return JSON.parse(jsonObjectText(text)) as OpenAIEnrichmentResponse;
  } catch {
    return null;
  }
}

function acceptedOpenAIFields(response: OpenAIEnrichmentResponse | null) {
  return [
    trustedOpenAIValue(response?.habitat) ? "habitat" : null,
    trustedOpenAIValue(response?.diet) ? "diet" : null,
    trustedOpenAIValue(response?.lifespan) ? "lifespan" : null,
    trustedOpenAIValue(response?.distribution) ? "distribution" : null,
    trustedOpenAIValue(response?.conservationStatus) ? "conservationStatus" : null,
  ].filter(Boolean);
}

function acceptedOpenAISourceUrls(response: OpenAIEnrichmentResponse | null) {
  return [...new Set([
    trustedOpenAIValue(response?.habitat) ? clean(response?.habitat?.sourceUrl) : null,
    trustedOpenAIValue(response?.diet) ? clean(response?.diet?.sourceUrl) : null,
    trustedOpenAIValue(response?.lifespan) ? clean(response?.lifespan?.sourceUrl) : null,
    trustedOpenAIValue(response?.distribution) ? clean(response?.distribution?.sourceUrl) : null,
    trustedOpenAIValue(response?.conservationStatus) ? clean(response?.conservationStatus?.sourceUrl) : null,
  ].filter(Boolean))];
}

function openAiPrompt(input: {
  canonicalName: string;
  commonName: string | null;
  scientificName: string;
  taxonomy: string | null;
  habitat: string | null;
  diet: string | null;
  lifespan: string | null;
  distribution: string | null;
  conservationStatus: string | null;
}, missingFields: string[]) {
  return [
    "You enrich public species reference data for a field journal app.",
    "Fill ONLY requested missing fields.",
    "Use web search when needed. Return a value only when reliable public sources support it.",
    "Use concise, user-facing field text. Do not mention uncertainty inside value.",
    "If no reliable source is found, set value and sourceUrl to empty strings.",
    "Never infer, guess, or invent facts.",
    `Species canonical name: ${input.canonicalName}`,
    `Scientific name: ${input.scientificName}`,
    `Common name: ${input.commonName ?? "unknown"}`,
    `Taxonomy: ${input.taxonomy ?? "unknown"}`,
    `Missing fields: ${missingFields.join(", ")}`,
  ].join("\n");
}

function openAiResponseSchema() {
  const fieldSchema = {
    type: "object",
    additionalProperties: false,
    properties: {
      value: { type: "string", description: "Concise field value, or empty string if unsupported." },
      sourceUrl: { type: "string", description: "URL supporting this exact value, or empty string if unsupported." },
    },
    required: ["value", "sourceUrl"],
  };

  return {
    type: "object",
    additionalProperties: false,
    properties: {
      habitat: fieldSchema,
      diet: fieldSchema,
      lifespan: fieldSchema,
      distribution: fieldSchema,
      conservationStatus: fieldSchema,
    },
    required: ["habitat", "diet", "lifespan", "distribution", "conservationStatus"],
  };
}

function jsonObjectText(text: string) {
  const cleaned = text.trim().replace(/^```json\s*/i, "").replace(/^```\s*/i, "").replace(/\s*```$/i, "");
  const start = cleaned.indexOf("{");
  const end = cleaned.lastIndexOf("}");
  if (start === -1 || end === -1 || end <= start) return cleaned;
  return cleaned.slice(start, end + 1);
}

function todayLabel() {
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date());
}

function clean(value: string | null | undefined) {
  const cleaned = value?.trim();
  return cleaned ? cleaned : null;
}

function matchesSpeciesName(gbif: GbifSpecies | null, canonicalName: string) {
  const canonical = normalizeName(canonicalName);
  const gbifCanonical = normalizeName(gbif?.canonicalName);
  const gbifScientific = normalizeName(gbif?.scientificName);

  return Boolean(
    canonical &&
    (gbifCanonical === canonical || gbifScientific === canonical || gbifScientific.startsWith(`${canonical} `))
  );
}

function normalizeName(value: string | null | undefined) {
  return clean(value)
    ?.toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim()
    .replace(/\s+/g, " ") ?? "";
}

function cleanSentence(value: string) {
  return value
    .replace(/=+\s*([^=]+?)\s*=+/g, "$1.")
    .replace(/\s+/g, " ")
    .replace(/\s+([,.;:])/g, "$1")
    .trim();
}

function splitSentences(text: string | null | undefined) {
  return (clean(text) ?? "")
    .split(/(?<=[.!?])\s+/)
    .map(cleanSentence)
    .filter(Boolean);
}

function sectionText(extract: string, heading: string) {
  const lines = extract.split("\n");
  const collected: string[] = [];
  let collecting = false;
  let headingDepth = 0;

  for (const line of lines) {
    const headingMatch = line.match(/^(=+)\s*(.+?)\s*\1$/);
    if (headingMatch) {
      const currentDepth = headingMatch[1].length;
      const currentHeading = headingMatch[2].trim().toLowerCase();
      if (collecting && currentDepth <= headingDepth) break;
      if (currentHeading === heading.toLowerCase()) {
        collecting = true;
        headingDepth = currentDepth;
      }
      continue;
    }

    if (collecting && clean(line)) {
      collected.push(line.trim());
    }
  }

  return collected.join(" ");
}

function truncate(value: string | null, maxLength: number) {
  if (!value) return notEnriched;
  if (value.length <= maxLength) return value;
  return `${value.slice(0, maxLength - 1).trim()}…`;
}

function safeDecode(value: string) {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

function isUsefulValue(value: string | null | undefined): value is string {
  const cleaned = clean(value)?.toLowerCase();
  return Boolean(cleaned && !["unknown", "not recorded", "not available", "n/a"].includes(cleaned));
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: jsonHeaders,
  });
}
