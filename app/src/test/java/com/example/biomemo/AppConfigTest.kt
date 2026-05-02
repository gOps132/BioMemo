package com.example.biomemo

import com.example.biomemo.config.AppConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {
    @Test
    fun unconfiguredKeysAreDetectedAsMissing() {
        assertFalse(AppConfig.hasSupabaseConfig())
        assertFalse(AppConfig.hasGoogleClientId())
        assertFalse(AppConfig.hasAiIdentificationApiKey())
    }

    @Test
    fun placeholderValuesAreNeverTreatedAsConfigured() {
        assertTrue(AppConfig.isMissing(""))
        assertTrue(AppConfig.isMissing("TODO"))
        assertTrue(AppConfig.isMissing("YOUR_SUPABASE_URL"))
        assertFalse(AppConfig.isMissing("https://example.supabase.co"))
    }
}
