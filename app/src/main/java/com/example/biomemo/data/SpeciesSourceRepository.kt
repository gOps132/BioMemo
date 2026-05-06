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

class SpeciesSourceRepository(
    private val gateway: SpeciesSourceGateway = SupabaseSpeciesSourceGateway()
) {
    suspend fun searchSpecies(query: String): List<SpeciesSearchResult> {
        val cleanedQuery = query.trim()
        if (cleanedQuery.isEmpty()) return emptyList()

        return gateway.searchGbifSpecies(cleanedQuery)
            .filter { it.rank.equals(SPECIES_RANK, ignoreCase = true) }
            .sortedWith(compareByDescending<GbifSpeciesSearchRow> { it.taxonomicStatus.equals(ACCEPTED_STATUS, ignoreCase = true) })
            .distinctBy { it.canonicalUsageKey() }
            .filter { it.taxonomicStatus.equals(ACCEPTED_STATUS, ignoreCase = true) || it.acceptedKey != null }
            .map { it.toSearchResult() }
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

    private fun GbifSpeciesSearchRow.canonicalUsageKey(): Int = acceptedKey ?: nubKey ?: key

    private companion object {
        const val SPECIES_RANK = "SPECIES"
        const val ACCEPTED_STATUS = "ACCEPTED"
    }
}

class SupabaseSpeciesSourceGateway(
    private val endpointUrl: String = AppConfig.supabaseUrl.trimEnd('/') + "/functions/v1/species-search",
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
