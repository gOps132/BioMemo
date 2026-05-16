package com.example.biomemo.data

import com.example.biomemo.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

interface SpeciesSourceGateway {
    suspend fun searchGbifSpecies(query: String): List<GbifSpeciesSearchRow>
    suspend fun previewSpeciesEnrichment(species: SpeciesSearchResult): SpeciesEnrichmentPreview
}

data class SpeciesSearchResult(
    val gbifUsageKey: Int,
    val scientificName: String,
    val canonicalName: String,
    val commonName: String?,
    val rank: String,
    val taxonomicStatus: String,
    val kingdom: String?,
    val phylum: String?,
    val className: String?,
    val order: String?,
    val family: String?,
    val genus: String?,
    val sourceName: String = "GBIF"
)

@Serializable
data class SpeciesEnrichmentPreview(
    val commonName: String? = null,
    val scientificName: String? = null,
    val taxonomy: String? = null,
    val habitat: String? = null,
    val diet: String? = null,
    val lifespan: String? = null,
    val distribution: String? = null,
    val conservationStatus: String? = null,
    val sourceApi: String? = null,
    val lastEnrichedDate: String? = null,
    val photoUrl: String? = null,
    val photoAttribution: String? = null,
    val photoLicense: String? = null,
    val photoSource: String? = null
)

class SpeciesSourceRepository(
    private val gateway: SpeciesSourceGateway = SupabaseSpeciesSourceGateway(),
    private val cache: SpeciesSourceCache = if (gateway is SupabaseSpeciesSourceGateway) {
        SpeciesSourceCache.shared
    } else {
        SpeciesSourceCache()
    }
) {
    suspend fun searchSpecies(query: String): List<SpeciesSearchResult> {
        val cleanedQuery = query.trim()
        if (cleanedQuery.isEmpty()) return emptyList()
        val cacheKey = cleanedQuery.normalized()
        cache.searchResults[cacheKey]?.let { return it }

        return gateway.searchGbifSpecies(cleanedQuery)
            .filter { it.rank.equals(SPECIES_RANK, ignoreCase = true) }
            .sortedWith(
                compareByDescending<GbifSpeciesSearchRow> { it.searchScore(cleanedQuery) }
                    .thenByDescending { it.taxonomicStatus.equals(ACCEPTED_STATUS, ignoreCase = true) }
            )
            .distinctBy { it.canonicalUsageKey() }
            .filter { it.taxonomicStatus.equals(ACCEPTED_STATUS, ignoreCase = true) || it.acceptedKey != null }
            .map { it.toSearchResult() }
            .also { cache.searchResults[cacheKey] = it }
    }

    suspend fun previewEnrichment(species: SpeciesSearchResult): SpeciesEnrichmentPreview {
        val cacheKey = species.enrichmentCacheKey()
        cache.enrichmentPreviews[cacheKey]?.let { return it }
        return gateway.previewSpeciesEnrichment(species)
            .also { cache.enrichmentPreviews[cacheKey] = it }
    }

    private fun GbifSpeciesSearchRow.toSearchResult(): SpeciesSearchResult {
        return SpeciesSearchResult(
            gbifUsageKey = canonicalUsageKey(),
            scientificName = accepted ?: scientificName,
            canonicalName = canonicalName,
            commonName = englishCommonName(),
            rank = rank,
            taxonomicStatus = if (acceptedKey != null && !taxonomicStatus.equals(ACCEPTED_STATUS, ignoreCase = true)) {
                ACCEPTED_STATUS
            } else {
                taxonomicStatus
            },
            kingdom = kingdom,
            phylum = phylum,
            className = className,
            order = order,
            family = family,
            genus = genus
        )
    }

    private fun GbifSpeciesSearchRow.englishCommonName(): String? {
        return vernacularNames
            .firstOrNull { it.language.equals("eng", ignoreCase = true) || it.language.equals("en", ignoreCase = true) }
            ?.vernacularName
            ?.takeIf { it.isNotBlank() }
    }

    private fun GbifSpeciesSearchRow.searchScore(query: String): Int {
        val normalizedQuery = query.normalized()
        val common = englishCommonName().normalized()
        val canonical = canonicalName.normalized()
        val scientific = scientificName.normalized()
        val kingdomScore = if (kingdom.equals("Heunggongvirae", ignoreCase = true)) -25 else 0

        return when {
            common == normalizedQuery -> 100
            common.endsWith(" $normalizedQuery") -> 95
            common.startsWith(normalizedQuery) -> 90
            canonical == normalizedQuery || scientific == normalizedQuery -> 80
            common.contains(normalizedQuery) -> 70
            canonical.startsWith(normalizedQuery) || scientific.startsWith(normalizedQuery) -> 60
            canonical.contains(normalizedQuery) || scientific.contains(normalizedQuery) -> 40
            else -> 0
        } + kingdomScore
    }

    private fun GbifSpeciesSearchRow.canonicalUsageKey(): Int = acceptedKey ?: nubKey ?: key

    private fun String?.normalized(): String {
        return this
            ?.trim()
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9]+"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
    }

    private companion object {
        const val SPECIES_RANK = "SPECIES"
        const val ACCEPTED_STATUS = "ACCEPTED"
    }
}

class SpeciesSourceCache {
    val searchResults: MutableMap<String, List<SpeciesSearchResult>> = mutableMapOf()
    val enrichmentPreviews: MutableMap<String, SpeciesEnrichmentPreview> = mutableMapOf()

    companion object {
        val shared = SpeciesSourceCache()
    }
}

private fun SpeciesSearchResult.enrichmentCacheKey(): String {
    return listOf(gbifUsageKey.toString(), scientificName.normalized(), canonicalName.normalized())
        .joinToString("|")
}

private fun String?.normalized(): String {
    return this
        ?.trim()
        ?.lowercase()
        ?.replace(Regex("[^a-z0-9]+"), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        .orEmpty()
}

class SupabaseSpeciesSourceGateway(
    private val endpointUrl: String = AppConfig.supabaseUrl.trimEnd('/') + "/functions/v1/species-search",
    private val enrichmentEndpointUrl: String = AppConfig.supabaseUrl.trimEnd('/') + "/functions/v1/species-enrichment-preview",
    private val anonKey: String = AppConfig.supabaseAnonKey,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SpeciesSourceGateway {
    override suspend fun searchGbifSpecies(query: String): List<GbifSpeciesSearchRow> = withContext(Dispatchers.IO) {
        require(AppConfig.hasSupabaseConfig()) {
            "Supabase URL and anon key must be configured in local.properties"
        }

        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $anonKey")
        }

        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(json.encodeToString(SpeciesSearchRequest(query)))
        }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        connection.disconnect()

        if (responseCode !in 200..299) {
            throw IllegalStateException("Species search failed: HTTP $responseCode")
        }

        json.decodeFromString<SpeciesSearchResponse>(body).results
    }

    override suspend fun previewSpeciesEnrichment(species: SpeciesSearchResult): SpeciesEnrichmentPreview = withContext(Dispatchers.IO) {
        require(AppConfig.hasSupabaseConfig()) {
            "Supabase URL and anon key must be configured in local.properties"
        }

        val connection = (URL(enrichmentEndpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $anonKey")
        }

        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(json.encodeToString(SpeciesEnrichmentRequest(species)))
        }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        connection.disconnect()

        if (responseCode !in 200..299) {
            throw IllegalStateException("Species enrichment failed: HTTP $responseCode")
        }

        json.decodeFromString<SpeciesEnrichmentPreview>(body)
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
    }
}

@Serializable
private data class SpeciesSearchRequest(
    val query: String
)

@Serializable
private data class SpeciesSearchResponse(
    val results: List<GbifSpeciesSearchRow> = emptyList()
)

@Serializable
private data class SpeciesEnrichmentRequest(
    val gbifUsageKey: Int,
    val scientificName: String,
    val canonicalName: String,
    val commonName: String? = null,
    val rank: String,
    val taxonomicStatus: String,
    val kingdom: String? = null,
    val phylum: String? = null,
    @SerialName("className") val className: String? = null,
    val order: String? = null,
    val family: String? = null,
    val genus: String? = null
) {
    constructor(species: SpeciesSearchResult) : this(
        gbifUsageKey = species.gbifUsageKey,
        scientificName = species.scientificName,
        canonicalName = species.canonicalName,
        commonName = species.commonName,
        rank = species.rank,
        taxonomicStatus = species.taxonomicStatus,
        kingdom = species.kingdom,
        phylum = species.phylum,
        className = species.className,
        order = species.order,
        family = species.family,
        genus = species.genus
    )
}

@Serializable
data class GbifSpeciesSearchRow(
    val key: Int,
    @SerialName("acceptedKey") val acceptedKey: Int? = null,
    @SerialName("nubKey") val nubKey: Int? = null,
    val accepted: String? = null,
    val scientificName: String,
    val canonicalName: String,
    val rank: String,
    val taxonomicStatus: String,
    val kingdom: String? = null,
    val phylum: String? = null,
    @SerialName("class") val className: String? = null,
    val order: String? = null,
    val family: String? = null,
    val genus: String? = null,
    val vernacularNames: List<GbifVernacularName> = emptyList()
)

@Serializable
data class GbifVernacularName(
    val vernacularName: String,
    val language: String = ""
)
