import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.File
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ── SOURCE UNIQUE DE VÉRITÉ (voir gradle.properties) ───────────────────
val sankamusicVersion: String = providers.gradleProperty("SANKAMUSIC_VERSION").get()
val sankamusicVersionCode: Int = providers.gradleProperty("SANKAMUSIC_VERSION_CODE").get().toInt()

// ── Signature CI (release.yml) ──────────────────────────────────────────
// Secrets attendus (jamais commités) :
//   ANDROID_KEYSTORE_BASE64      keystore encodé en base64
//   ANDROID_KEYSTORE_PASSWORD    mot de passe du keystore
//   ANDROID_KEY_ALIAS            alias de la clé
//   ANDROID_KEY_PASSWORD         mot de passe de la clé
// En local (sans secrets) : null → build release NON signé (nommé « -unsigned »),
// ce qui fait échouer volontairement la publication (règle RELEASE_GUIDE.md).
private data class CiSigning(
    val keystoreFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

// Résolu UNE FOIS, en config. `layout.buildDirectory` est disponible ici car la
// fonction est une closure sur le script de build (accès à l'extension `layout`).
private fun loadCiSigning(): CiSigning? {
    val keystoreBase64 = System.getenv("ANDROID_KEYSTORE_BASE64") ?: return null
    val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: return null
    val alias = System.getenv("ANDROID_KEY_ALIAS") ?: return null
    val keyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: return null

    val keystoreFile = layout.buildDirectory.file("signing/release-keystore.jks").get().asFile
    keystoreFile.parentFile.mkdirs()
    keystoreFile.writeBytes(Base64.getDecoder().decode(keystoreBase64))
    return CiSigning(keystoreFile, keystorePassword, alias, keyPassword)
}

private val ciSigning = loadCiSigning()

android {
    namespace = "com.sankamusic.app"
    compileSdk = 35

    // Signing config injectée depuis l'env CI — déclarée dans `signingConfigs {}`
    // où `storeFile` / `storePassword` / `keyAlias` / `keyPassword` résolvent bien
    // (contrairement à une fonction libre, qui causait un échec de compilation du script).
    signingConfigs {
        if (ciSigning != null) {
            create("ciRelease") {
                storeFile = ciSigning.keystoreFile
                storePassword = ciSigning.storePassword
                keyAlias = ciSigning.keyAlias
                keyPassword = ciSigning.keyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.sankamusic.app"
        minSdk = 26
        targetSdk = 35
        versionCode = sankamusicVersionCode
        versionName = sankamusicVersion

        // Exposé au code applicatif (affichage « À propos ») — même source de vérité.
        buildConfigField("String", "SANKAMUSIC_VERSION", "\"$sankamusicVersion\"")
        buildConfigField("String", "SANKAMUSIC_UPSTREAM_BASE", "\"SimpMusic\"")
        buildConfigField("String", "SANKAMUSIC_UPSTREAM_VERSION", "\"TBD\"") // voir docs/UPSTREAM_SYSTEM.md
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // null (local, sans secrets) → APK non signé.
            signingConfig = if (ciSigning != null) signingConfigs.getByName("ciRelease") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // ── Nom d'artefact : Sankamusic-v<version>.apk (RELEASE_GUIDE.md § 2) ──
    // L'API `outputFileName` a été retirée de `VariantOutput` en AGP 8.x ; on passe
    // par `applicationVariants` + l'implémentation interne `BaseVariantOutputImpl`,
    // le pattern de facto standard en AGP 8.x pour renommer l'APK de sortie.
    applicationVariants.all {
        outputs.all {
            if (buildType.name == "release") {
                (this as BaseVariantOutputImpl).outputFileName =
                    "Sankamusic-v${sankamusicVersion}.apk"
            }
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":plugins:hellospacekai"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}