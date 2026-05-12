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

type GeminiEnrichmentResponse = {
  habitat?: EnrichmentField;
  diet?: EnrichmentField;
  lifespan?: EnrichmentField;
  distribution?: EnrichmentField;
};

type GeminiGroundingMetadata = {
  webSearchQueries?: string[];
  groundingChunks?: Array<{
    web?: {
      uri?: string;
      title?: string;
    };
  }>;
  groundingSupports?: Array<{
    segment?: {
      text?: string;
    };
    groundingChunkIndices?: number[];
  }>;
};

type GeminiGenerateContentResponse = {
  candidates?: Array<{
    content?: {
      parts?: Array<{
        text?: string;
      }>;
    };
    groundingMetadata?: GeminiGroundingMetadata;
  }>;
};

type GeminiFallbackResult = {
  enrichment: GeminiEnrichmentResponse | null;
  debug: {
    attempted: boolean;
    hasApiKey: boolean;
    missingFields: string[];
    httpStatus?: number;
    parseOk?: boolean;
    grounded?: boolean;
    searchQueries?: string[];
    sourceUrls?: string[];
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
  const conservationStatus = conservationFrom(iucnAssessment, inatTaxon, occurrences);
  const photo = photoFrom(inatTaxon);
  const geminiFallback = await fetchGeminiFallback({
    canonicalName,
    commonName: clean(inatTaxon?.preferred_common_name) ?? clean(body.commonName),
    scientificName: clean(gbifSpecies?.scientificName) ?? clean(body.scientificName) ?? canonicalName,
    taxonomy,
    habitat,
    diet,
    lifespan,
    distribution,
  });
  const geminiEnrichment = geminiFallback?.enrichment ?? null;
  habitat = habitat ?? trustedGeminiValue(geminiEnrichment?.habitat);
  diet = diet ?? trustedGeminiValue(geminiEnrichment?.diet);
  lifespan = lifespan ?? trustedGeminiValue(geminiEnrichment?.lifespan);
  distribution = distribution ?? trustedGeminiValue(geminiEnrichment?.distribution);

  return jsonResponse({
    commonName: clean(inatTaxon?.preferred_common_name) ?? clean(body.commonName) ?? canonicalName,
    scientificName: clean(gbifSpecies?.scientificName) ?? clean(body.scientificName) ?? canonicalName,
    taxonomy,
    habitat,
    diet,
    lifespan,
    distribution,
    conservationStatus,
    sourceApi: sourceApi(inatTaxon, wikipediaSummary, wikipediaExtract, iucnAssessment, geminiEnrichment),
    lastEnrichedDate: todayLabel(),
    photoUrl: photo?.url ?? notEnriched,
    photoAttribution: photo?.attribution ?? notEnriched,
    photoLicense: photo?.license ?? notEnriched,
    photoSource: photo?.source ?? notEnriched,
    ...(body.debugGemini ? { geminiDebug: geminiFallback?.debug ?? null } : {}),
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

async function fetchGeminiFallback(input: {
  canonicalName: string;
  commonName: string | null;
  scientificName: string;
  taxonomy: string | null;
  habitat: string | null;
  diet: string | null;
  lifespan: string | null;
  distribution: string | null;
}) {
  const missingFields = [
    input.habitat ? null : "habitat",
    input.diet ? null : "diet",
    input.lifespan ? null : "lifespan",
    input.distribution ? null : "distribution",
  ].filter(Boolean);
  const apiKey = Deno.env.get("GEMINI_API_KEY");
  const baseDebug = {
    attempted: Boolean(apiKey && missingFields.length > 0),
    hasApiKey: Boolean(apiKey),
    missingFields: missingFields as string[],
  };
  if (!apiKey || missingFields.length === 0) return { enrichment: null, debug: baseDebug };

  const geminiResponse = await fetch(
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        "x-goog-api-key": apiKey,
        "User-Agent": "BioMemo/1.0 species-enrichment-preview (Supabase Edge Function)",
      },
      body: JSON.stringify({
        contents: [{
          parts: [{
            text: geminiPrompt(input, missingFields as string[]),
          }],
        }],
        tools: [
          { googleSearch: {} },
          { urlContext: {} },
        ],
      }),
    },
  ).catch(() => null);
  if (!geminiResponse?.ok) {
    return {
      enrichment: null,
      debug: {
        ...baseDebug,
        httpStatus: geminiResponse?.status,
      },
    };
  }

  const response = (await geminiResponse.json().catch(() => null)) as GeminiGenerateContentResponse | null;
  const candidate = response?.candidates?.[0];
  const text = candidate?.content?.parts?.[0]?.text;
  if (!text) {
    return {
      enrichment: null,
      debug: {
        ...baseDebug,
        httpStatus: geminiResponse.status,
        grounded: hasGoogleSearchGrounding(candidate?.groundingMetadata),
        searchQueries: candidate?.groundingMetadata?.webSearchQueries ?? [],
        sourceUrls: groundingSourceUrls(candidate?.groundingMetadata),
      },
    };
  }

  const grounded = hasGoogleSearchGrounding(candidate?.groundingMetadata);
  if (!grounded) {
    return {
      enrichment: null,
      debug: {
        ...baseDebug,
        httpStatus: geminiResponse.status,
        parseOk: false,
        grounded,
        searchQueries: candidate?.groundingMetadata?.webSearchQueries ?? [],
        sourceUrls: groundingSourceUrls(candidate?.groundingMetadata),
      },
    };
  }

  const parsed = parseGeminiResponse(text);
  const enrichment = parsed ? withGroundingSourceUrls(parsed, candidate?.groundingMetadata) : null;
  return {
    enrichment,
    debug: {
      ...baseDebug,
      httpStatus: geminiResponse.status,
      parseOk: Boolean(parsed),
      grounded,
      searchQueries: candidate?.groundingMetadata?.webSearchQueries ?? [],
      sourceUrls: groundingSourceUrls(candidate?.groundingMetadata),
      acceptedFields: acceptedGeminiFields(enrichment),
      acceptedSourceUrls: acceptedGeminiSourceUrls(enrichment),
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
  gemini: GeminiEnrichmentResponse | null,
) {
  return ["GBIF", inat ? "iNaturalist" : null, iucn ? "IUCN Red List" : null, wikipedia || wikipediaExtract ? "Wikipedia" : null, hasTrustedGeminiValue(gemini) ? "Gemini Search" : null]
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

function trustedGeminiValue(field: EnrichmentField | undefined) {
  if (!clean(field?.value) || !clean(field?.sourceUrl)) return null;
  return clean(field?.value);
}

function hasTrustedGeminiValue(response: GeminiEnrichmentResponse | null) {
  return Boolean(
    trustedGeminiValue(response?.habitat) ||
    trustedGeminiValue(response?.diet) ||
    trustedGeminiValue(response?.lifespan) ||
    trustedGeminiValue(response?.distribution)
  );
}

function parseGeminiResponse(text: string) {
  try {
    return JSON.parse(jsonObjectText(text)) as GeminiEnrichmentResponse;
  } catch {
    return null;
  }
}

function hasGoogleSearchGrounding(metadata: GeminiGroundingMetadata | undefined) {
  return Boolean(
    metadata?.webSearchQueries?.length ||
    metadata?.groundingChunks?.some((chunk) => clean(chunk.web?.uri))
  );
}

function withGroundingSourceUrls(
  response: GeminiEnrichmentResponse,
  metadata: GeminiGroundingMetadata | undefined,
) {
  return {
    habitat: withGroundingSourceUrl(response.habitat, metadata),
    diet: withGroundingSourceUrl(response.diet, metadata),
    lifespan: withGroundingSourceUrl(response.lifespan, metadata),
    distribution: withGroundingSourceUrl(response.distribution, metadata),
  };
}

function withGroundingSourceUrl(
  field: EnrichmentField | undefined,
  metadata: GeminiGroundingMetadata | undefined,
) {
  const value = clean(field?.value);
  if (!field || !value || clean(field.sourceUrl)) return field;

  const sourceUrl = groundingSourceUrlFor(value, metadata);
  return sourceUrl ? { ...field, sourceUrl } : field;
}

function groundingSourceUrlFor(value: string, metadata: GeminiGroundingMetadata | undefined) {
  const chunks = metadata?.groundingChunks ?? [];
  const supports = metadata?.groundingSupports ?? [];
  const normalizedValue = normalizeText(value);

  const matchingSupport = supports.find((support) => {
    const segment = normalizeText(support.segment?.text);
    return segment && (segment.includes(normalizedValue) || normalizedValue.includes(segment));
  });
  const supportedUrl = matchingSupport?.groundingChunkIndices
    ?.map((index) => clean(chunks[index]?.web?.uri))
    .find(Boolean);
  if (supportedUrl) return supportedUrl;

  return chunks.length === 1 ? clean(chunks[0]?.web?.uri) : null;
}

function groundingSourceUrls(metadata: GeminiGroundingMetadata | undefined) {
  return [...new Set((metadata?.groundingChunks ?? []).map((chunk) => clean(chunk.web?.uri)).filter(Boolean))];
}

function acceptedGeminiFields(response: GeminiEnrichmentResponse | null) {
  return [
    trustedGeminiValue(response?.habitat) ? "habitat" : null,
    trustedGeminiValue(response?.diet) ? "diet" : null,
    trustedGeminiValue(response?.lifespan) ? "lifespan" : null,
    trustedGeminiValue(response?.distribution) ? "distribution" : null,
  ].filter(Boolean);
}

function acceptedGeminiSourceUrls(response: GeminiEnrichmentResponse | null) {
  return [...new Set([
    trustedGeminiValue(response?.habitat) ? clean(response?.habitat?.sourceUrl) : null,
    trustedGeminiValue(response?.diet) ? clean(response?.diet?.sourceUrl) : null,
    trustedGeminiValue(response?.lifespan) ? clean(response?.lifespan?.sourceUrl) : null,
    trustedGeminiValue(response?.distribution) ? clean(response?.distribution?.sourceUrl) : null,
  ].filter(Boolean))];
}

function geminiPrompt(input: {
  canonicalName: string;
  commonName: string | null;
  scientificName: string;
  taxonomy: string | null;
  habitat: string | null;
  diet: string | null;
  lifespan: string | null;
  distribution: string | null;
}, missingFields: string[]) {
  return [
    "You enrich public species reference data for a field journal app.",
    "Fill ONLY requested missing fields.",
    "Use Google Search or URL context. Return a value only when a reliable source clearly supports it.",
    "Use concise, user-facing field text. Do not mention uncertainty inside value.",
    "If no reliable source is found, set value and sourceUrl to empty strings.",
    "Never infer, guess, or invent facts.",
    "Return ONLY valid JSON, no markdown, matching this exact shape:",
    JSON.stringify(geminiResponseSchema()),
    `Species canonical name: ${input.canonicalName}`,
    `Scientific name: ${input.scientificName}`,
    `Common name: ${input.commonName ?? "unknown"}`,
    `Taxonomy: ${input.taxonomy ?? "unknown"}`,
    `Missing fields: ${missingFields.join(", ")}`,
  ].join("\n");
}

function geminiResponseSchema() {
  const fieldSchema = {
    type: "object",
    properties: {
      value: { type: "string", description: "Concise field value, or empty string if unsupported." },
      sourceUrl: { type: "string", description: "URL supporting this exact value, or empty string if unsupported." },
    },
    required: ["value", "sourceUrl"],
  };

  return {
    type: "object",
    properties: {
      habitat: fieldSchema,
      diet: fieldSchema,
      lifespan: fieldSchema,
      distribution: fieldSchema,
    },
    required: ["habitat", "diet", "lifespan", "distribution"],
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

function normalizeText(value: string | null | undefined) {
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
