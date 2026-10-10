import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.aboutlibraries.android)
}

// Licenses screen: the plugin collects all dependencies + their licenses at build time.
// Code copied into the app (no dependency) is added as JSON in <root>/config/libraries/.
aboutLibraries {
    collect {
        configPath = rootProject.layout.projectDirectory.dir("config")
    }
}

// versionCode = 300 + number of commits
val commitCount = providers.exec {
    commandLine("git", "rev-list", "HEAD", "--count")
}.standardOutput.asText.map { it.trim().toInt() }

android {
    namespace = "org.qstuff.qplayer"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.qstuff.qdeq"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 300 + commitCount.get()
        versionName = "3.0.0"
        multiDexEnabled = true
        vectorDrawables.useSupportLibrary = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lint {
        abortOnError = false
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }

        create("beta") {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isDebuggable = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        release {
            isMinifyEnabled = false
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation 3 (one activity, screens as back stack entries)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.json)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Koin module injection
    implementation(libs.koin.android)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Media3 (ExoPlayer) — playback + pitch/tempo
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.common)
    implementation(libs.media3.session)

    // Logging
    implementation(libs.kermit)

    // Settings storage (SharedPreferences on Android)
    implementation(libs.multiplatform.settings)

    // Licenses screen
    implementation(libs.aboutlibraries.compose.m3)

    // Crash reporting (opt-in, see CrashReporting)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    testImplementation(libs.junit)
    testImplementation(libs.multiplatform.settings.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
