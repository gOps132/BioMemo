package com.example.biomemo.config

import com.example.biomemo.BuildConfig

object AppConfig {
    val supabaseUrl: String = BuildConfig.SUPABASE_URL
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY
    val googleWebClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID
    val aiIdentificationApiKey: String = BuildConfig.AI_IDENTIFICATION_API_KEY
    const val authRedirectScheme: String = "biomemo"
    const val authRedirectHost: String = "auth-callback"
    const val authRedirectUrl: String = "$authRedirectScheme://$authRedirectHost"

    fun hasSupabaseConfig(): Boolean = !isMissing(supabaseUrl) && !isMissing(supabaseAnonKey)
    fun hasGoogleClientId(): Boolean = !isMissing(googleWebClientId)
    fun hasAiIdentificationApiKey(): Boolean = !isMissing(aiIdentificationApiKey)

    fun isMissing(value: String?): Boolean {
        val cleaned = value?.trim().orEmpty()
        return cleaned.isEmpty() || cleaned.equals("TODO", ignoreCase = true) || cleaned.startsWith("YOUR_")
    }
}
