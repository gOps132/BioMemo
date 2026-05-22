package com.example.biomemo.data

import com.example.biomemo.config.AppConfig
import com.example.biomemo.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.hours

class SupabaseBioRecordGateway(
    private val client: SupabaseClient = SupabaseClientProvider.client,
    private val identifyEndpointUrl: String = AppConfig.supabaseUrl.trimEnd('/') + "/functions/v1/identify-biorecord-image",
    private val anonKey: String = AppConfig.supabaseAnonKey,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : BioRecordGateway {
    override suspend fun fetchBioRecords(limit: Int?): List<BioRecordRow> {
        val userId = currentUserId() ?: return emptyList()

        return client.from("bio_records")
            .select(BIO_RECORD_COLUMNS) {
                filter {
                    eq("user_id", userId)
                }
                order("saved_at", Order.DESCENDING)
                limit?.let { limit(it.toLong()) }
            }
            .decodeList<BioRecordRow>()
    }

    override suspend fun fetchIdentificationCandidates(): List<IdentificationCandidateRow> {
        return client.from("identification_candidates")
            .select()
            .decodeList<IdentificationCandidateRow>()
    }

    override suspend fun fetchSpeciesProfiles(): List<SpeciesProfileRow> {
        return client.from("species_profiles")
            .select()
            .decodeList<SpeciesProfileRow>()
    }

    override fun observeBioRecordChanges(): Flow<Unit> = callbackFlow {
        val userId = currentUserId()
        if (userId == null) {
            close()
            return@callbackFlow
        }

        val recordChannel = client.channel("bio-records-all-$userId")
        val recordChanges = recordChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "bio_records"
            filter("user_id", FilterOperator.EQ, userId)
        }

        val candidateChannel = client.channel("bio-record-candidates-all-$userId")
        val candidateChanges = candidateChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "identification_candidates"
        }

        val speciesChannel = client.channel("bio-record-species-all-$userId")
        val speciesChanges = speciesChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "species_profiles"
        }

        val recordJob = launch {
            recordChanges.collect { trySend(Unit) }
        }
        val candidateJob = launch {
            candidateChanges.collect { trySend(Unit) }
        }
        val speciesJob = launch {
            speciesChanges.collect { trySend(Unit) }
        }

        recordChannel.subscribe(blockUntilSubscribed = true)
        candidateChannel.subscribe(blockUntilSubscribed = true)
        speciesChannel.subscribe(blockUntilSubscribed = true)

        awaitClose {
            recordJob.cancel()
            candidateJob.cancel()
            speciesJob.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { recordChannel.unsubscribe() }
                runCatching { candidateChannel.unsubscribe() }
                runCatching { speciesChannel.unsubscribe() }
                runCatching { client.realtime.removeChannel(recordChannel) }
                runCatching { client.realtime.removeChannel(candidateChannel) }
                runCatching { client.realtime.removeChannel(speciesChannel) }
            }
        }
    }

    override suspend fun deleteBioRecords(ids: List<String>, photoPaths: List<String>) {
        if (ids.isEmpty()) return
        val userId = currentUserId() ?: error("Sign in before deleting BioRecords.")
        client.from("bio_records")
            .delete {
                filter {
                    eq("user_id", userId)
                    isIn("id", ids)
                }
            }
        if (photoPaths.isNotEmpty()) {
            runCatching {
                client.storage.from(BIORECORD_PHOTO_BUCKET).delete(photoPaths)
            }
        }
    }

    override suspend fun fetchBioRecordById(id: String): BioRecordRow? {
        val userId = currentUserId() ?: return null

        return client.from("bio_records")
            .select(BIO_RECORD_COLUMNS) {
                filter {
                    eq("user_id", userId)
                    eq("id", id)
                }
                limit(1)
            }
            .decodeList<BioRecordRow>()
            .firstOrNull()
    }

    override fun observeBioRecord(id: String): Flow<BioRecordRow> = callbackFlow {
        suspend fun sendLatest() {
            fetchBioRecordById(id)?.let { trySend(it) }
        }

        val recordChannel = client.channel("bio-record-$id")
        val recordChanges = recordChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "bio_records"
            filter("id", FilterOperator.EQ, id)
        }

        val speciesChannel = client.channel("bio-record-$id-species")
        val speciesChanges = speciesChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "species_profiles"
        }

        val candidateChannel = client.channel("bio-record-$id-candidates")
        val candidateChanges = candidateChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "identification_candidates"
            filter("bio_record_id", FilterOperator.EQ, id)
        }

        val recordJob = launch {
            recordChanges.collect { sendLatest() }
        }
        val speciesJob = launch {
            speciesChanges.collect { sendLatest() }
        }
        val candidateJob = launch {
            candidateChanges.collect { sendLatest() }
        }

        sendLatest()
        recordChannel.subscribe(blockUntilSubscribed = true)
        speciesChannel.subscribe(blockUntilSubscribed = true)
        candidateChannel.subscribe(blockUntilSubscribed = true)

        awaitClose {
            recordJob.cancel()
            speciesJob.cancel()
            candidateJob.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { recordChannel.unsubscribe() }
                runCatching { speciesChannel.unsubscribe() }
                runCatching { candidateChannel.unsubscribe() }
                runCatching { client.realtime.removeChannel(recordChannel) }
                runCatching { client.realtime.removeChannel(speciesChannel) }
                runCatching { client.realtime.removeChannel(candidateChannel) }
            }
        }
    }

    override suspend fun currentUserId(): String? {
        return client.auth.currentSessionOrNull()?.user?.id ?: client.auth.currentUserOrNull()?.id
    }

    override suspend fun uploadBioRecordPhoto(path: String, bytes: ByteArray, contentType: String) {
        client.storage.from(BIORECORD_PHOTO_BUCKET)
            .upload(path, bytes) {
                upsert = false
                this.contentType = ContentType.parse(contentType)
            }
    }

    override suspend fun insertBioRecordDraft(draft: NewBioRecordDraft): BioRecordRow {
        return client.from("bio_records")
            .insert(draft) {
                select()
            }
            .decodeSingle<BioRecordRow>()
    }

    override suspend fun insertImageMetadata(metadata: NewImageMetadata) {
        client.from("image_metadata")
            .insert(metadata)
    }

    override suspend fun identifyBioRecordImage(recordId: String): List<IdentificationCandidateRow> = withContext(Dispatchers.IO) {
        val accessToken = client.auth.currentSessionOrNull()?.accessToken
            ?: error("Sign in before identifying BioRecord photos.")
        val connection = (URL(identifyEndpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = IDENTIFY_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(json.encodeToString(IdentifyBioRecordRequest(recordId)))
        }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        connection.disconnect()

        if (responseCode !in 200..299) {
            throw IllegalStateException("Image identification failed: HTTP $responseCode")
        }

        json.decodeFromString<IdentifyBioRecordResponse>(body).candidates
    }

    override suspend fun upsertBioRecordSpeciesProfile(profile: BioRecordSpeciesProfileUpsert): SpeciesProfileRow {
        return client.postgrest
            .rpc(
                function = "upsert_biorecord_species_profile",
                parameters = profile.toJsonObject()
            )
            .decodeSingle<SpeciesProfileRow>()
    }

    override suspend fun createSignedPhotoUrl(path: String): String {
        return client.storage.from(BIORECORD_PHOTO_BUCKET)
            .createSignedUrl(path, expiresIn = 1.hours)
    }

    private companion object {
        const val BIORECORD_PHOTO_BUCKET = "biorecord-photos"
        const val TIMEOUT_MS = 15_000
        const val IDENTIFY_TIMEOUT_MS = 45_000
        val BIO_RECORD_COLUMNS = Columns.raw(
            """
            id,
            user_id,
            species_profile_id,
            photo_url,
            thumbnail_url,
            source_type,
            observed_at,
            saved_at,
            latitude,
            longitude,
            location_label,
            notes,
            confidence_score,
            verification_status,
            metadata_availability,
            species_profiles(
                id,
                common_name,
                scientific_name,
                taxonomy,
                habitat,
                diet,
                lifespan,
                distribution,
                conservation_status,
                source_api,
                last_enriched_at
            )
            """.trimIndent()
        )
    }
}

@Serializable
private data class IdentifyBioRecordRequest(
    val bioRecordId: String
)

@Serializable
private data class IdentifyBioRecordResponse(
    val candidates: List<IdentificationCandidateRow> = emptyList()
)

private fun BioRecordSpeciesProfileUpsert.toJsonObject(): JsonObject {
    return buildJsonObject {
        put("p_bio_record_id", bioRecordId)
        put("p_common_name", commonName)
        put("p_scientific_name", scientificName)
        put("p_taxonomy", taxonomy)
        put("p_habitat", habitat)
        put("p_diet", diet)
        put("p_lifespan", lifespan)
        put("p_distribution", distribution)
        put("p_conservation_status", conservationStatus)
        put("p_source_api", sourceApi)
    }
}
