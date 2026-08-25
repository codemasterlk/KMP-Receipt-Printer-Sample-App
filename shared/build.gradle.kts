import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    jvm()
    
    android {
       namespace = "com.kmpgaraj.kmpescposprintersampleapp.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            // rememberBluetoothRefresh's Android actual: runtime permission request + Context.
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)

            // TerminalPrinterKMP — DSL/layout core, renderer, bundled fonts, session handling,
            // and every transport. Published as one artifact per module (no umbrella artifact),
            // so each is its own dependency; escpos-transport-serial/-spooler stub Unavailable on
            // Android/iOS and escpos-transport-bt stubs Unavailable on iOS (real elsewhere) —
            // see TransportKind.kt's KDoc.
            implementation(libs.escpos.core)
            implementation(libs.escpos.render)
            implementation(libs.escpos.session)
            implementation(libs.escpos.fonts)
            implementation(libs.escpos.transport.api)
            implementation(libs.escpos.transport.bt)
            implementation(libs.escpos.transport.usb)
            implementation(libs.escpos.transport.serial)
            implementation(libs.escpos.transport.spooler)
            implementation(libs.escpos.transport.tcp)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}