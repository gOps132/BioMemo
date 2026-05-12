type GbifVernacularName = {
  vernacularName?: string;
  language?: string;
};

type GbifSpeciesRow = {
  key: number;
  acceptedKey?: number;
  nubKey?: number;
  accepted?: string;
  scientificName?: string;
  canonicalName?: string;
  rank?: string;
  taxonomicStatus?: string;
  kingdom?: string;
  phylum?: string;
  class?: string;
  order?: string;
  family?: string;
  genus?: string;
  vernacularNames?: GbifVernacularName[];
};

type GbifSearchResponse = {
  results?: GbifSpeciesRow[];
};

type GbifMatchResponse = {
  usageKey?: number;
  acceptedUsageKey?: number;
  scientificName?: string;
  canonicalName?: string;
  rank?: string;
  status?: string;
  kingdom?: string;
  phylum?: string;
  class?: string;
  order?: string;
  family?: string;
  genus?: string;
};

type InatTaxon = {
  name?: string;
  rank?: string;
  preferred_common_name?: string;
};

type InatTaxaResponse = {
  results?: InatTaxon[];
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

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (request.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }

  const body = await request.json().catch(() => ({}));
  const query = typeof body.query === "string" ? body.query.trim() : "";

  if (!query) {
    return jsonResponse({ results: [] });
  }

  const gbifUrl = new URL("https://api.gbif.org/v1/species/search");
  gbifUrl.searchParams.set("q", query);
  gbifUrl.searchParams.set("rank", "SPECIES");
  gbifUrl.searchParams.set("limit", "20");

  const [gbifResponse, inatRows] = await Promise.all([
    fetch(gbifUrl, {
      headers: {
        Accept: "application/json",
        "User-Agent": "BioMemo/1.0 species-search (Supabase Edge Function)",
      },
    }),
    fetchInatGbifRows(query),
  ]);

  if (!gbifResponse.ok) {
    return jsonResponse({ error: "GBIF species search failed" }, 502);
  }

  const gbifBody = (await gbifResponse.json()) as GbifSearchResponse;
  const results = normalizeGbifRows([...inatRows, ...(gbifBody.results ?? [])], query);

  return jsonResponse({ results });
});

async function fetchInatGbifRows(query: string) {
  const inatUrl = new URL("https://api.inaturalist.org/v1/taxa");
  inatUrl.searchParams.set("q", query);
  inatUrl.searchParams.set("rank", "species");
  inatUrl.searchParams.set("per_page", "8");

  const inatResponse = await fetchJson<InatTaxaResponse>(inatUrl.toString());
  const taxa = (inatResponse?.results ?? [])
    .filter((taxon) => taxon.rank?.toLowerCase() === "species" && taxon.name);

  const rows = await Promise.all(taxa.map(toGbifRow));
  return rows.filter((row): row is GbifSpeciesRow => row !== null);
}

async function toGbifRow(taxon: InatTaxon): Promise<GbifSpeciesRow | null> {
  if (!taxon.name) return null;

  const matchUrl = new URL("https://api.gbif.org/v1/species/match");
  matchUrl.searchParams.set("name", taxon.name);
  matchUrl.searchParams.set("rank", "SPECIES");

  const match = await fetchJson<GbifMatchResponse>(matchUrl.toString());
  const key = match?.acceptedUsageKey ?? match?.usageKey;
  if (!key || match?.rank?.toUpperCase() !== "SPECIES") return null;

  return {
    key,
    acceptedKey: match.acceptedUsageKey,
    nubKey: match.usageKey,
    scientificName: match.scientificName,
    canonicalName: match.canonicalName ?? taxon.name,
    rank: match.rank,
    taxonomicStatus: match.status,
    kingdom: match.kingdom,
    phylum: match.phylum,
    class: match.class,
    order: match.order,
    family: match.family,
    genus: match.genus,
    vernacularNames: taxon.preferred_common_name
      ? [{ vernacularName: taxon.preferred_common_name, language: "eng" }]
      : [],
  };
}

async function fetchJson<T>(url: string): Promise<T | null> {
  const response = await fetch(url, {
    headers: {
      Accept: "application/json",
      "User-Agent": "BioMemo/1.0 species-search (Supabase Edge Function)",
    },
  }).catch(() => null);

  if (!response?.ok) return null;
  return (await response.json().catch(() => null)) as T | null;
}

function normalizeGbifRows(rows: GbifSpeciesRow[], query: string) {
  const seen = new Set<number>();

  return rows
    .filter((row) => row.rank?.toUpperCase() === "SPECIES")
    .sort((a, b) => searchScore(b, query) - searchScore(a, query) || acceptedScore(b) - acceptedScore(a))
    .filter((row) => {
      const usageKey = canonicalUsageKey(row);
      const usable = row.taxonomicStatus?.toUpperCase() === "ACCEPTED" || row.acceptedKey !== undefined;
      if (!usable || seen.has(usageKey)) return false;
      seen.add(usageKey);
      return true;
    })
    .map((row) => ({
      key: canonicalUsageKey(row),
      acceptedKey: row.acceptedKey,
      nubKey: row.nubKey,
      accepted: row.accepted,
      scientificName: row.accepted ?? row.scientificName ?? row.canonicalName ?? "",
      canonicalName: row.canonicalName ?? row.scientificName ?? "",
      rank: row.rank ?? "SPECIES",
      taxonomicStatus: row.acceptedKey ? "ACCEPTED" : row.taxonomicStatus ?? "ACCEPTED",
      kingdom: row.kingdom,
      phylum: row.phylum,
      class: row.class,
      order: row.order,
      family: row.family,
      genus: row.genus,
      vernacularNames: (row.vernacularNames ?? []).filter((name) => Boolean(name.vernacularName)),
    }));
}

function acceptedScore(row: GbifSpeciesRow) {
  return row.taxonomicStatus?.toUpperCase() === "ACCEPTED" ? 1 : 0;
}

function searchScore(row: GbifSpeciesRow, query: string) {
  const normalizedQuery = normalize(query);
  const common = normalize(englishCommonName(row));
  const canonical = normalize(row.canonicalName);
  const scientific = normalize(row.scientificName);
  const kingdomScore = row.kingdom?.toLowerCase() === "heunggongvirae" ? -25 : 0;

  const matchScore =
    common === normalizedQuery ? 100 :
    common.endsWith(` ${normalizedQuery}`) ? 95 :
    common.startsWith(normalizedQuery) ? 90 :
    canonical === normalizedQuery || scientific === normalizedQuery ? 80 :
    common.includes(normalizedQuery) ? 70 :
    canonical.startsWith(normalizedQuery) || scientific.startsWith(normalizedQuery) ? 60 :
    canonical.includes(normalizedQuery) || scientific.includes(normalizedQuery) ? 40 :
    0;

  return matchScore + kingdomScore;
}

function englishCommonName(row: GbifSpeciesRow) {
  return row.vernacularNames?.find((name) => {
    const language = name.language?.toLowerCase();
    return language === "eng" || language === "en";
  })?.vernacularName;
}

function normalize(value: string | undefined) {
  return (value ?? "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function canonicalUsageKey(row: GbifSpeciesRow) {
  return row.acceptedKey ?? row.nubKey ?? row.key;
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: jsonHeaders,
  });
}
