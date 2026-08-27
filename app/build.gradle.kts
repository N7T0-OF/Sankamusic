import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ── SOURCE UNIQUE DE VÉRITÉ (voir gradle.properties) ───────────────────
val sankamusicVersion: String = providers.gradleProperty("SANKAMUSIC_VERSION").get()
val sankamusicVersionCode: Int = providers.gradleProperty("SANKAMUSIC_VERSION_CODE").get().toInt()

android {
    namespace = "com.sankamusic.app"
    compileSdk = 35

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
            // Signature CI : le keystore n'est JAMAIS commité. Il est injecté par
            // .github/workflows/release.yml via les secrets GitHub. Sans keystore,
            // pas de signingConfig → APK « -unsigned » → le CI ÉCHOUE (règle RELEASE_GUIDE.md).
            signingConfig = loadCiSigningConfig()
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

// ── Nom d'artefact : Sankamusic-v<version>.apk (RELEASE_GUIDE.md § 2) ──
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("Sankamusic-v${sankamusicVersion}.apk")
        }
    }
}

// ── Signature CI (release.yml) ──────────────────────────────────────────
// Secrets attendus :
//   ANDROID_KEYSTORE_BASE64      keystore encodé en base64
//   ANDROID_KEYSTORE_PASSWORD    mot de passe du keystore
//   ANDROID_KEY_ALIAS            alias de la clé
//   ANDROID_KEY_PASSWORD         mot de passe de la clé
// En local (sans secrets) : retourne null → build release non signé (nommé
// « -unsigned »), ce qui fait échouer volontairement la publication.
fun loadCiSigningConfig(): com.android.build.gradle.internal.dsl.SigningConfig? {
    val keystoreBase64 = System.getenv("ANDROID_KEYSTORE_BASE64") ?: return null
    val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: return null
    val keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: return null
    val keyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: return null

    val keystoreFile = layout.buildDirectory.file("signing/release-keystore.jks").get().asFile
    keystoreFile.parentFile.mkdirs()
    keystoreFile.writeBytes(Base64.getDecoder().decode(keystoreBase64))

    return signingConfigs.create("ciRelease") {
        storeFile = keystoreFile
        storePassword = keystorePassword
        keyAlias = keyAlias
        keyPassword = keyPassword
    }
}
