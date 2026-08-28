// ── Sankamusic Core ────────────────────────────────────────────────────
// Contient les CONTRATS de la plateforme : SpaceKai API (plugins, thèmes,
// update, upstream) et les moteurs (plugins, thèmes, mises à jour).
// Dépendances volontairement réduites : kotlinx-serialization (JSON des
// releases GitHub) ; coroutines uniquement en test (runBlocking).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "com.sankamusic.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // État réactif du moteur de thèmes (StateFlow consommé par l'UI).
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}
