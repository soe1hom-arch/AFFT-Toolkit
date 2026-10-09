import java.util.Calendar

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jlleitschuh.gradle.ktlint")
}

android {
    namespace = "com.afft.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.afft.app"
        minSdk = 26
        targetSdk = 35
        versionName = "2.3.0"
        // F-Droid/reproducible builds: pin via AFFT_VERSION_CODE env, else date-based.
        versionCode = System.getenv("AFFT_VERSION_CODE")?.toIntOrNull() ?: generateVersionCode()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release-key.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    androidResources {
        noCompress += "bin"
    }
}

// F-Droid: helper binaries in app/src/main/jniLibs are built from pinned
// upstream sources (tools/build_all.sh), never committed. Verify them before
// every build; build automatically when the NDK is available.
// Escape hatch for UI-only iteration: ./gradlew assembleDebug -PskipNativeBinaries
val ndkDirEnv: String? =
    System.getenv("ANDROID_NDK_HOME") ?: System.getenv("NDK_DIR")

tasks.register("ensureNativeBinaries") {
    doLast {
        if (project.hasProperty("skipNativeBinaries")) {
            logger.warn("Skipping native binaries check (-PskipNativeBinaries).")
            return@doLast
        }
        val verifyResult =
            project.exec {
                commandLine("bash", "tools/verify_binaries.sh")
                isIgnoreExitValue = true
            }
        if (verifyResult.exitValue == 0) return@doLast
        if (ndkDirEnv.isNullOrBlank()) {
            throw GradleException(
                "Native helper binaries are missing and no NDK was found. " +
                    "Set ANDROID_NDK_HOME (or NDK_DIR) and rebuild so " +
                    "tools/build_all.sh can compile them from source. " +
                    "See tools/README.md and F-DROID.md.",
            )
        }
        logger.lifecycle("Building native helper binaries from source (NDK: $ndkDirEnv)...")
        project.exec {
            environment("NDK_DIR", ndkDirEnv as String)
            commandLine("bash", "tools/build_all.sh")
        }
    }
}

tasks.named("preBuild") {
    dependsOn("ensureNativeBinaries")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

fun generateVersionCode(): Int {
    // YYMMDDnn berbasis tanggal: selalu naik antar-hari dan tidak bergantung
    // pada history git (dulu pakai `git rev-list --count HEAD` yang bisa
    // menghasilkan versionCode menurun saat build tanpa .git).
    val now = Calendar.getInstance()
    val year = now.get(Calendar.YEAR) % 100
    val month = now.get(Calendar.MONTH) + 1
    val day = now.get(Calendar.DAY_OF_MONTH)
    return year * 1_000_000 + month * 10_000 + day * 100 + 1
}
