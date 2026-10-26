import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Platform-independent code of the app (models, persistence, ViewModels, platform interfaces).
// Android implementations of the interfaces are in :androidApp; iOS follows in Phase 2.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.aboutlibraries)
}

kotlin {
    android {
        namespace = "org.qstuff.qplayer.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        withHostTest {}
        // Compose Multiplatform resources are packaged as Android assets
        androidResources {
            enable = true
        }
    }

    // The framework the iOS app (iosApp/) links; built by Xcode via embedAndSignAppleFrameworkForXcode.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    compilerOptions {
        // Room's generated database constructor is an expect/actual object (Beta in Kotlin).
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
            api(libs.room.runtime)
            api(libs.multiplatform.settings)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kermit)
            api(libs.compose.mp.runtime)
            api(libs.compose.mp.ui)
            api(libs.compose.mp.resources)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.material3)
            implementation(libs.compose.mp.material.icons.extended)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            // Licenses screen (the list: Android generated in :androidApp, iOS exported below)
            implementation(libs.aboutlibraries.compose.m3)
            // Long press + drag to reorder the queue and playlist tracks
            implementation(libs.reorderable)
        }
        iosMain.dependencies {
            implementation(libs.sqlite.bundled)
        }
        androidMain.dependencies {
            // Storage permission request
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Licenses screen on iOS: the iOS dependencies with their licenses (plus <root>/config/libraries/
// for code copied into the app), exported at build time as an iOS resource
// (files/aboutlibraries.json). Android generates its own list in :androidApp.
aboutLibraries {
    collect {
        configPath = rootProject.layout.projectDirectory.dir("config")
        // KMP metadata classpaths are compile classpaths, which are skipped by default.
        all = true
    }
    export {
        // The plugin doesn't see the iOS klib configurations; the iosMain metadata classpath has the
        // same libraries (commonMain + iosMain, nothing Android-only).
        variant = "metadataIosMain"
        outputFile = layout.buildDirectory.file("generated/iosLicenses/files/aboutlibraries.json")
    }
}
val exportIosLicenses = tasks.named("exportLibraryDefinitions")

// Texts, drawables and the privacy / imprint pages (src/commonMain/composeResources).
compose.resources {
    packageOfResClass = "org.qstuff.qplayer.resources"
    generateResClass = always
    customDirectory(
        sourceSetName = "iosMain",
        // Own folder: the plugin's other variants write to generated/aboutLibraries
        directoryProvider = exportIosLicenses.map { layout.buildDirectory.dir("generated/iosLicenses").get() }
    )
}

// Room: generate the database implementation for every platform.
dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
