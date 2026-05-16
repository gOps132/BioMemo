package com.example.biomemo.data.remote

import com.example.biomemo.config.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        require(AppConfig.hasSupabaseConfig()) {
            "Supabase URL and anon key must be configured in local.properties"
        }

        createSupabaseClient(
            supabaseUrl = AppConfig.supabaseUrl,
            supabaseKey = AppConfig.supabaseAnonKey
        ) {
            install(Auth) {
                scheme = AppConfig.authRedirectScheme
                host = AppConfig.authRedirectHost
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }
}
