plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidLibrary {
        namespace = "dev.maxxing.github.core.auth"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64 {
        // On the iOS Simulator, `SecItem*` from a raw `simctl spawn` test binary
        // (no host app, no provisioning profile) returns -34018
        // (`errSecMissingEntitlement`) unless the Mach-O carries a
        // `__TEXT,__entitlements` section. Embedding one in the *test* binary
        // lets the real-Keychain tests in `iosTest` run under
        // `:core:auth:iosSimulatorArm64Test`. Production binaries are untouched.
        val entitlementsFile = layout.projectDirectory.file("src/iosTest/keychain-entitlements.plist")
        binaries.getTest(org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.DEBUG).apply {
            linkerOpts(
                "-sectcreate",
                "__TEXT",
                "__entitlements",
                entitlementsFile.asFile.absolutePath,
            )
            // The plist is only referenced by path inside linkerOpts, so declare
            // it as a link-task input to make the section part of the up-to-date
            // check (editing the plist must relink the test binary).
            linkTaskProvider.configure { inputs.file(entitlementsFile) }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

// `KotlinNativeSimulatorTest` spawns the test binary with
// `xcrun simctl spawn --standalone`, i.e. outside `launchd_sim`. The simulator
// Keychain service (`securityd`) is not reachable from that context, so every
// `SecItem*` call returns -25291 (`errSecNotAvailable`) no matter what
// entitlements the binary carries. Spawning through `launchd_sim` (the default
// `simctl spawn` behaviour) makes the real Keychain reachable, which is what
// the `iosTest` Keychain tests need.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    standalone.set(false)
}
