// ── Plugin d'exemple : HelloSpaceKai (Phase 3 — validation du framework) ──
//
// Premier plugin SpaceKai : module INDÉPENDANT du Core, il ne dépend que de
// `:core` (l'API publique). Il démontre le manifest, le cycle de vie et la
// validation du framework.
//
// ⚠️ Squelette non compilé dans cet environnement.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sankamusic.plugins.hellospacekai"
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
