plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.bomeber.homestream"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bomeber.homestream"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }
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
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    // เตรียมไว้สำหรับ Step ถัดไป (ยังไม่ใช้งานจริงใน Step 0)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    // Step 4 — REQUIRED NEW DEPENDENCY: HLS playback (MediaAccessType.HLS)
    // needs the separate media3-exoplayer-hls artifact; it is NOT bundled
    // inside media3-exoplayer. Without it, DefaultMediaSourceFactory cannot
    // resolve an HlsMediaSource and HLS items will surface as a playback
    // error instead of a crash, but will never actually play.
    // ACTION NEEDED: add the matching alias to gradle/libs.versions.toml,
    // using the SAME version as the existing "media3" entries, e.g.:
    //   media3-exoplayer-hls = { group = "androidx.media3", name = "media3-exoplayer-hls", version.ref = "media3" }
    // This repo's libs.versions.toml was not part of what was shared in
    // this session, so the exact version ref name could not be confirmed —
    // adjust the alias below if your catalog names it differently.
    implementation(libs.media3.exoplayer.hls)

    implementation(libs.coil.compose)
}
