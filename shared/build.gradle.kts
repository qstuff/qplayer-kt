import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Platform-independent code of the app (models, persistence, ViewModels, platform interfaces).
// Android implementations of the interfaces are in :androidApp; iOS follows in Phase 2.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
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
    }

    // Compiled already, so platform-specific code can't slip into commonMain unnoticed.
    iosArm64()
    iosSimulatorArm64()

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
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Room: generate the database implementation for every platform.
dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
