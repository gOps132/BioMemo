type IdentifyRequest = {
  bioRecordId?: string;
};

type BioRecord = {
  id: string;
  photo_url: string | null;
};

type Candidate = {
  id?: string;
  bio_record_id?: string;
  common_name?: string | null;
  scientific_name?: string;
  confidence_score?: number | null;
  reasoning?: string | null;
  visible_traits?: string | null;
  uncertainty_notes?: string | null;
  selected?: boolean;
};

type GeminiGenerateContentResponse = {
  candidates?: Array<{
    content?: {
      parts?: Array<{
        text?: string;
      }>;
    };
  }>;
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

const bucketName = "biorecord-photos";
const maxImageBytes = 8 * 1024 * 1024;

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (request.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }

  const authHeader = request.headers.get("Authorization") ?? "";
  if (!authHeader.startsWith("Bearer ")) {
    return jsonResponse({ error: "Authorization bearer token is required" }, 401);
  }

  const body = (await request.json().catch(() => ({}))) as IdentifyRequest;
  const bioRecordId = clean(body.bioRecordId);
  if (!bioRecordId) {
    return jsonResponse({ error: "bioRecordId is required" }, 400);
  }

  const existingCandidates = await fetchExistingCandidates(bioRecordId, authHeader);
  if (existingCandidates.length > 0) {
    return jsonResponse({ candidates: existingCandidates, reused: true });
  }

  const record = await fetchBioRecord(bioRecordId, authHeader);
  if (!record) {
    return jsonResponse({ error: "BioRecord not found" }, 404);
  }
  if (!record.photo_url) {
    return jsonResponse({ error: "BioRecord has no photo_url" }, 400);
  }

  const image = await fetchStoredImage(record.photo_url, authHeader);
  if (!image) {
    return jsonResponse({ error: "BioRecord photo could not be read" }, 404);
  }
  if (image.bytes.byteLength > maxImageBytes) {
    return jsonResponse({ error: "BioRecord photo is too large for inline identification" }, 413);
  }

  const candidates = await identifyImage(image);
  if (candidates.length === 0) {
    await updateBioRecordStatus(bioRecordId, authHeader, { verification_status: "failed" });
    return jsonResponse({ candidates: [] });
  }

  const insertedCandidates = await insertCandidates(bioRecordId, candidates, authHeader);
  if (insertedCandidates.length === 0) {
    return jsonResponse({ error: "Identification candidates could not be saved" }, 500);
  }
  const bestCandidate = insertedCandidates[0] ?? candidates[0];
  await updateBioRecordStatus(bioRecordId, authHeader, {
    confidence_score: bestCandidate.confidence_score ?? null,
    verification_status: "needs confirmation",
  });

  return jsonResponse({ candidates: insertedCandidates });
});

async function fetchBioRecord(bioRecordId: string, authHeader: string): Promise<BioRecord | null> {
  const url = `${supabaseUrl()}/rest/v1/bio_records?id=eq.${encodeURIComponent(bioRecordId)}&select=id,photo_url`;
  const response = await fetch(url, {
    headers: supabaseHeaders(authHeader),
  }).catch(() => null);
  if (!response?.ok) return null;
  const records = (await response.json().catch(() => [])) as BioRecord[];
  return records[0] ?? null;
}

async function fetchExistingCandidates(bioRecordId: string, authHeader: string): Promise<Candidate[]> {
  const url = `${supabaseUrl()}/rest/v1/identification_candidates?bio_record_id=eq.${encodeURIComponent(bioRecordId)}&select=id,bio_record_id,common_name,scientific_name,confidence_score,reasoning,visible_traits,uncertainty_notes,selected&order=confidence_score.desc.nullslast`;
  const response = await fetch(url, {
    headers: supabaseHeaders(authHeader),
  }).catch(() => null);
  if (!response?.ok) return [];
  return ((await response.json().catch(() => [])) as Candidate[]).sort(candidateSort);
}

async function fetchStoredImage(path: string, authHeader: string): Promise<{ bytes: Uint8Array; mimeType: string } | null> {
  const encodedPath = path.split("/").map(encodeURIComponent).join("/");
  const response = await fetch(`${supabaseUrl()}/storage/v1/object/${bucketName}/${encodedPath}`, {
    headers: supabaseHeaders(authHeader),
  }).catch(() => null);
  if (!response?.ok) return null;
  const bytes = new Uint8Array(await response.arrayBuffer());
  const mimeType = response.headers.get("content-type")?.split(";")[0] ?? mimeTypeFromPath(path);
  return { bytes, mimeType };
}

async function identifyImage(image: { bytes: Uint8Array; mimeType: string }): Promise<Candidate[]> {
  const apiKey = Deno.env.get("GEMINI_API_KEY");
  if (!apiKey) {
    return [];
  }

  const response = await fetch(
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        "x-goog-api-key": apiKey,
        "User-Agent": "BioMemo/1.0 identify-biorecord-image (Supabase Edge Function)",
      },
      body: JSON.stringify({
        contents: [{
          parts: [
            { text: identificationPrompt() },
            {
              inline_data: {
                mime_type: image.mimeType,
                data: base64FromBytes(image.bytes),
              },
            },
          ],
        }],
      }),
    },
  ).catch(() => null);
  if (!response?.ok) return [];

  const gemini = (await response.json().catch(() => null)) as GeminiGenerateContentResponse | null;
  const text = gemini?.candidates?.[0]?.content?.parts?.[0]?.text;
  return parseCandidateResponse(text).slice(0, 3);
}

async function insertCandidates(bioRecordId: string, candidates: Candidate[], authHeader: string): Promise<Candidate[]> {
  const rows = candidates.map((candidate, index) => ({
    bio_record_id: bioRecordId,
    common_name: clean(candidate.common_name) ?? null,
    scientific_name: clean(candidate.scientific_name) ?? "Unidentified organism",
    confidence_score: boundedConfidence(candidate.confidence_score),
    reasoning: clean(candidate.reasoning) ?? null,
    visible_traits: clean(candidate.visible_traits) ?? null,
    uncertainty_notes: clean(candidate.uncertainty_notes) ?? null,
    selected: index === 0,
  }));
  const response = await fetch(`${supabaseUrl()}/rest/v1/identification_candidates?select=id,bio_record_id,common_name,scientific_name,confidence_score,reasoning,visible_traits,uncertainty_notes,selected`, {
    method: "POST",
    headers: {
      ...supabaseHeaders(authHeader),
      "Content-Type": "application/json",
      Prefer: "return=representation",
    },
    body: JSON.stringify(rows),
  }).catch(() => null);
  if (!response?.ok) return [];
  return ((await response.json().catch(() => rows)) as Candidate[]).sort(candidateSort);
}

async function updateBioRecordStatus(
  bioRecordId: string,
  authHeader: string,
  patch: { confidence_score?: number | null; verification_status: "needs confirmation" | "failed" },
) {
  await fetch(`${supabaseUrl()}/rest/v1/bio_records?id=eq.${encodeURIComponent(bioRecordId)}`, {
    method: "PATCH",
    headers: {
      ...supabaseHeaders(authHeader),
      "Content-Type": "application/json",
    },
    body: JSON.stringify(patch),
  }).catch(() => null);
}

function parseCandidateResponse(text: string | undefined): Candidate[] {
  if (!text) return [];
  const jsonText = text
    .replace(/^```json\s*/i, "")
    .replace(/^```\s*/i, "")
    .replace(/```\s*$/i, "")
    .trim();
  const parsed = safeParseCandidates(jsonText);
  if (!parsed) return [];
  return (parsed.candidates ?? [])
    .map((candidate) => ({
      common_name: clean(candidate.common_name),
      scientific_name: clean(candidate.scientific_name),
      confidence_score: boundedConfidence(candidate.confidence_score),
      reasoning: clean(candidate.reasoning),
      visible_traits: clean(candidate.visible_traits),
      uncertainty_notes: clean(candidate.uncertainty_notes),
    }))
    .filter((candidate) => Boolean(candidate.scientific_name));
}

function safeParseCandidates(jsonText: string): { candidates?: Candidate[] } | null {
  try {
    return JSON.parse(jsonText) as { candidates?: Candidate[] };
  } catch (_) {
    return null;
  }
}

function identificationPrompt() {
  return [
    "Identify the organism in this field photo for a nature journaling app.",
    "Return strict JSON only, no markdown.",
    "Schema: {\"candidates\":[{\"common_name\":\"\",\"scientific_name\":\"\",\"confidence_score\":0,\"reasoning\":\"\",\"visible_traits\":\"\",\"uncertainty_notes\":\"\"}]}",
    "Return up to 3 candidate species. Use scientific binomials when possible.",
    "Confidence score must be 0 to 100. Be conservative when image quality is poor.",
    "If no organism is visible, return {\"candidates\":[]}.",
  ].join("\n");
}

function supabaseHeaders(authHeader: string) {
  return {
    Accept: "application/json",
    apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "",
    Authorization: authHeader,
  };
}

function supabaseUrl() {
  return Deno.env.get("SUPABASE_URL") ?? "";
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: jsonHeaders,
  });
}

function clean(value: unknown): string | null {
  return typeof value === "string" ? value.trim() || null : null;
}

function boundedConfidence(value: unknown): number | null {
  const numberValue = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(numberValue)) return null;
  return Math.max(0, Math.min(100, Math.round(numberValue)));
}

function candidateSort(left: Candidate, right: Candidate) {
  if (left.selected !== right.selected) return left.selected ? -1 : 1;
  return (right.confidence_score ?? -1) - (left.confidence_score ?? -1);
}

function mimeTypeFromPath(path: string) {
  const extension = path.split(".").pop()?.toLowerCase();
  if (extension === "png") return "image/png";
  if (extension === "webp") return "image/webp";
  if (extension === "heic") return "image/heic";
  if (extension === "heif") return "image/heif";
  return "image/jpeg";
}

function base64FromBytes(bytes: Uint8Array) {
  let binary = "";
  const chunkSize = 0x8000;
  for (let index = 0; index < bytes.length; index += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(index, index + chunkSize));
  }
  return btoa(binary);
}
