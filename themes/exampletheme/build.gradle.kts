// ── Thème d'exemple : ExampleTheme (Phase 3 — validation du framework) ──
//
// Deuxième volet de la validation : un thème complet basé sur
// `SpaceKaiThemeTokens`, indépendant du Core (ne dépend que de `:core`).
//
// ⚠️ Squelette non compilé dans cet environnement.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sankamusic.themes.exampletheme"
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
    implementation(project(":core"))
    testImplementation(libs.junit)
}
