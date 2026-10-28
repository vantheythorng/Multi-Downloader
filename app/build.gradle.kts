import com.android.build.api.variant.impl.VariantOutputImpl
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing: secret/keystore.properties (git-ignored) holds storeFile, storePassword, keyAlias and
// keyPassword; storeFile is relative to the project root. Without that file, release builds stay unsigned.
val releaseKeystore: Properties? = rootProject.file("secret/keystore.properties")
    .takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

android {
    namespace = "com.kwan.multidownloader"
    compileSdk {
        // core-ktx 1.19 / lifecycle 2.11 require compiling against API 37; targetSdk stays 36.
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kwan.multidownloader"
        minSdk = 29
        targetSdk = 36
        // Both live in gradle/libs.versions.toml; versionCode is YYYYMMDDNN.
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()

        // Modern 64-bit ARM phones only: one APK, one copy of the native Python/FFmpeg (~61 MB).
        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = rootProject.file(releaseKeystore.getProperty("storeFile"))
                storePassword = releaseKeystore.getProperty("storePassword")
                keyAlias = releaseKeystore.getProperty("keyAlias")
                keyPassword = releaseKeystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 drops unused code (mostly material-icons-extended): roughly 70 MB less per APK.
            isMinifyEnabled = true
            isShrinkResources = true
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
    buildFeatures {
        compose = true
        buildConfig = true // VERSION_NAME / VERSION_CODE for Settings → About
    }
    // Required by youtubedl-android: the Python/ffmpeg binaries are extracted from jniLibs at runtime.
    packaging {
        jniLibs.useLegacyPackaging = true
    }
}

// APK names: <name>_<dev|pro>_<versionName>_<versionCode>.apk, e.g. Multi-Downloader_pro_1.0.0_2026102801.apk.
// AGP 9 has no public API for this; VariantOutputImpl.outputFileName is the supported-in-practice hook.
androidComponents {
    onVariants { variant ->
        val stage = if (variant.buildType == "release") "pro" else "dev"
        variant.outputs.forEach { output ->
            (output as? VariantOutputImpl)?.outputFileName?.set(
                output.versionName.zip(output.versionCode) { name, code -> "Multi-Downloader_${stage}_${name}_$code.apk" }
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Room's MigrationTestHelper reads the exported schemas from test assets.
android.sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)
    implementation(libs.youtubedl.aria2c)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
