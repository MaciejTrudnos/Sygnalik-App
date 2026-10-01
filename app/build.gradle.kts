import java.io.File

private fun loadDotEnv(directory: File): Map<String, String> {
    val envFile = File(directory, ".env")
    if (!envFile.isFile) {
        return emptyMap()
    }
    return envFile.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val entry = line.removePrefix("export ").trim()
            val separatorIndex = entry.indexOf('=')
            if (separatorIndex <= 0) {
                null
            } else {
                val key = entry.substring(0, separatorIndex).trim()
                var value = entry.substring(separatorIndex + 1).trim()
                if (value.length >= 2) {
                    val first = value.first()
                    val last = value.last()
                    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                        value = value.substring(1, value.length - 1)
                    }
                }
                key to value
            }
        }
        .toMap()
}

val dotEnvValues: Map<String, String> = loadDotEnv(rootDir)

fun configValue(key: String, default: String = ""): String =
    dotEnvValues[key] ?: System.getenv(key) ?: default

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.maciejtrudnos.sygnalik"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.maciejtrudnos.sygnalik"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "NOMINATIM_USER_AGENT",
            "\"${configValue("NOMINATIM_USER_AGENT")}\""
        )

        buildConfigField(
            "String",
            "TRACCAR_HOST",
            "\"${configValue("TRACCAR_HOST")}\""
        )

        buildConfigField(
            "String",
            "TRACCAR_DEVICE_ID",
            "\"${configValue("TRACCAR_DEVICE_ID")}\""
        )

        buildConfigField(
            "String",
            "WARNING_GATEWAY_HOST",
            "\"${configValue("WARNING_GATEWAY_HOST")}\""
        )

        buildConfigField(
            "String",
            "WARNING_GATEWAY_API_KEY",
            "\"${configValue("WARNING_GATEWAY_API_KEY")}\""
        )

        buildConfigField(
            "String",
            "GRAPHHOPPER_HOST",
            "\"${configValue("GRAPHHOPPER_HOST", "http://129.159.245.14:8989")}\""
        )
    }

    buildTypes {
        release {
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.play.services.location)
    implementation(libs.gson)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}