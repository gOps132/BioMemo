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

  const gbifResponse = await fetch(gbifUrl, {
    headers: {
      Accept: "application/json",
      "User-Agent": "BioMemo/1.0 species-search (Supabase Edge Function)",
    },
  });

  if (!gbifResponse.ok) {
    return jsonResponse({ error: "GBIF species search failed" }, 502);
  }

  const gbifBody = (await gbifResponse.json()) as GbifSearchResponse;
  const results = normalizeGbifRows(gbifBody.results ?? []);

  return jsonResponse({ results });
});

function normalizeGbifRows(rows: GbifSpeciesRow[]) {
  const seen = new Set<number>();

  return rows
    .filter((row) => row.rank?.toUpperCase() === "SPECIES")
    .sort((a, b) => acceptedScore(b) - acceptedScore(a))
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

function canonicalUsageKey(row: GbifSpeciesRow) {
  return row.acceptedKey ?? row.nubKey ?? row.key;
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: jsonHeaders,
  });
}
