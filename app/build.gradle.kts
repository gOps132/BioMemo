import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

fun localPropertiesFiles(): List<File> {
    val worktreeRoot = rootProject.projectDir
    val parentCheckoutProperties = worktreeRoot.parentFile
        ?.takeIf { it.name == ".worktrees" }
        ?.parentFile
        ?.resolve("local.properties")
    return listOfNotNull(parentCheckoutProperties, rootProject.file("local.properties"))
        .distinctBy { it.absolutePath }
}

val localProperties = Properties().apply {
    localPropertiesFiles().forEach { localPropertiesFile ->
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { load(it) }
        }
    }
}

fun localPropertyWithFallback(name: String, fallbackName: String, defaultValue: String = ""): String =
    localProperties.getProperty(name)
        ?: localProperties.getProperty(fallbackName)
        ?: defaultValue

fun localProperty(name: String, defaultValue: String = ""): String =
    localProperties.getProperty(name, defaultValue)

android {
    namespace = "com.example.biomemo"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.biomemo"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProperty("GOOGLE_WEB_CLIENT_ID")}\"")
        buildConfigField("boolean", "LOCAL_SUPABASE", "false")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "SUPABASE_URL", "\"${localPropertyWithFallback("SUPABASE_DEV_URL", "SUPABASE_URL")}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localPropertyWithFallback("SUPABASE_DEV_ANON_KEY", "SUPABASE_ANON_KEY")}\"")
            buildConfigField("boolean", "LOCAL_SUPABASE", "${localProperties.containsKey("SUPABASE_DEV_URL") && localProperties.containsKey("SUPABASE_DEV_ANON_KEY")}")
        }
        create("prodDebug") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "SUPABASE_URL", "\"${localPropertyWithFallback("SUPABASE_PROD_URL", "SUPABASE_URL")}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localPropertyWithFallback("SUPABASE_PROD_ANON_KEY", "SUPABASE_ANON_KEY")}\"")
            buildConfigField("boolean", "LOCAL_SUPABASE", "false")
        }
        release {
            buildConfigField("String", "SUPABASE_URL", "\"${localPropertyWithFallback("SUPABASE_PROD_URL", "SUPABASE_URL")}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localPropertyWithFallback("SUPABASE_PROD_ANON_KEY", "SUPABASE_ANON_KEY")}\"")
            buildConfigField("boolean", "LOCAL_SUPABASE", "false")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.osmdroid.android)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
