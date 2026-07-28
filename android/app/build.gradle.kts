import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * `local.properties` holds the SDK path, the Supabase credentials, and
 * the release signing material. It is gitignored — see
 * android/local.properties.example for the template and CLAUDE.md §9 for
 * why none of it may be committed.
 *
 * Absent entries resolve to empty rather than failing the build: a fresh
 * clone must be able to compile before anyone has been given credentials.
 * The app validates them at startup in M6.5, which is where an empty URL
 * becomes a clear error rather than a confusing network failure.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun localProperty(name: String): String = localProperties.getProperty(name).orEmpty()

android {
    namespace = "com.snjewellery.admin"

    /**
     * compileSdk tracks the latest stable platform: it only affects what
     * the code may compile against, never what devices can install.
     */
    compileSdk = 35

    defaultConfig {
        applicationId = "com.snjewellery.admin"

        /**
         * **minSdk 26 — Android 8.0 (2017).** Reasoning in
         * docs/architecture/android-build.md §2. In short: this is an
         * internal tool for three or four known people, and locking out
         * a working phone would mean one of them cannot upload at all.
         * The Photo Picker gives M7 a single permission-free path on
         * every supported version, so nothing is complicated by it.
         */
        minSdk = 26

        /**
         * targetSdk declares which behaviour changes the app has been
         * tested against. It matches compileSdk deliberately.
         */
        targetSdk = 35

        versionCode = 1
        versionName = "0.1.0"

        // Distribution is a direct APK (ADR-0007), so there is no store
        // listing to carry release notes — versionName is what an admin
        // reads in the about screen to know whether they are current.

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${localProperty("SUPABASE_URL")}\"")
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${localProperty("SUPABASE_ANON_KEY")}\"",
        )
    }

    /**
     * Release signing, per ADR-0007's direct-APK decision. Configured
     * only when the keystore material is present, so a debug build works
     * on a machine that has never seen the keystore.
     *
     * The keystore file itself lives outside the repository. Losing it
     * means no existing install can ever be updated — Android refuses an
     * APK signed by a different key.
     */
    val releaseStoreFile = localProperty("RELEASE_STORE_FILE")

    signingConfigs {
        if (releaseStoreFile.isNotEmpty()) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = localProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // So a debug build can sit alongside a release one on the
            // same phone — the owner keeps the working app while a build
            // is being tested.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    /**
     * The generated token file is compiled from where the generator
     * writes it, rather than being copied into the source tree.
     *
     * That keeps it visibly not-hand-written: `android/design-tokens/` is
     * an output directory, and nobody editing `ui/theme/` can mistake it
     * for a file they may change. Regenerate with
     * `node tools/generate-tokens.mjs` (ADR-0008).
     */
    sourceSets {
        getByName("main") {
            kotlin.srcDir("../design-tokens")
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

    lint {
        // Every suppression is in app/lint.xml with its reason.
        warningsAsErrors = false
        abortOnError = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // collectAsStateWithLifecycle — a screen must not keep collecting
    // while it is off screen.
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Tooling is debug-only: @Preview rendering must not ship in the APK.
    debugImplementation(libs.androidx.compose.ui.tooling)
}
