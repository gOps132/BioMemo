package com.example.biomemo

import com.example.biomemo.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {
    @Test
    fun configStatusMatchesLocalProperties() {
        assertEquals(
            !AppConfig.isMissing(AppConfig.supabaseUrl) && !AppConfig.isMissing(AppConfig.supabaseAnonKey),
            AppConfig.hasSupabaseConfig()
        )
        assertEquals(!AppConfig.isMissing(AppConfig.googleWebClientId), AppConfig.hasGoogleClientId())
        assertEquals(!AppConfig.isMissing(AppConfig.aiIdentificationApiKey), AppConfig.hasAiIdentificationApiKey())
    }

    @Test
    fun placeholderValuesAreNeverTreatedAsConfigured() {
        assertTrue(AppConfig.isMissing(""))
        assertTrue(AppConfig.isMissing("TODO"))
        assertTrue(AppConfig.isMissing("YOUR_SUPABASE_URL"))
        assertFalse(AppConfig.isMissing("https://example.supabase.co"))
    }
}
