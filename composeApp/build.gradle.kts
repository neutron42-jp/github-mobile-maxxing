import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val defaultGitHubClientId: String = localProps.getProperty("github.client.id", "Ov23lihCAIZTj1Puaf9r")
    .replace("\"", "")
    .trim()
    .ifEmpty { "Ov23lihCAIZTj1Puaf9r" }

fun runGitCommand(vararg args: String): String? = runCatching {
    val proc = ProcessBuilder("git", *args)
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val output = proc.inputStream.bufferedReader().readText().trim()
    val exit = proc.waitFor()
    if (exit == 0 && output.isNotEmpty()) output else null
}.getOrNull()

fun escapeKotlinStringLiteral(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

val resolvedGitCommitCount: Int = runGitCommand("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
val resolvedCommitSha: String = runGitCommand("rev-parse", "--short", "HEAD") ?: "unknown"
val resolvedAppVersionName: String = System.getenv("ANDROID_VERSION_NAME")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "1.0.$resolvedGitCommitCount"

val generateMaxxingConfig by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/maxxingConfig/kotlin")
    inputs.property("clientId", defaultGitHubClientId)
    inputs.property("versionName", resolvedAppVersionName)
    inputs.property("commitSha", resolvedCommitSha)
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile.resolve("dev/maxxing/github/app")
        dir.mkdirs()
        val safeClientId = escapeKotlinStringLiteral(defaultGitHubClientId)
        val safeVersionName = escapeKotlinStringLiteral(resolvedAppVersionName)
        val safeCommitSha = escapeKotlinStringLiteral(resolvedCommitSha)
        dir.resolve("MaxxingBuildConfig.kt").writeText(
            """
            package dev.maxxing.github.app

            internal object MaxxingBuildConfig {
                const val DEFAULT_GITHUB_CLIENT_ID: String = "$safeClientId"
                const val APP_VERSION_NAME: String = "$safeVersionName"
                const val APP_COMMIT_SHA: String = "$safeCommitSha"
            }
            """.trimIndent()
        )
    }
}

kotlin {
    androidLibrary {
        namespace = "dev.maxxing.github.app.shared"
        compileSdk {
            version = release(37) {
                minorApiLevel = 2
            }
        }
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources {
            enable = true
        }
        // JVM tests for the Context-free font helpers in androidMain.
        withHostTest {}
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generateMaxxingConfig.map { layout.buildDirectory.dir("generated/maxxingConfig/kotlin") })
            dependencies {
                implementation(projects.core.model)
                api(projects.core.auth)
                implementation(projects.core.network)
                implementation(projects.core.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)
            }
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.work.runtime)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val desktopTest by getting {
            dependencies {
                // Drives the real network-layer producers in the i18n contract tests.
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val androidHostTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kxml2)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "dev.maxxing.github.app.resources"
}

compose.desktop {
    application {
        mainClass = "dev.maxxing.github.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "github-mobile-maxxing"
            packageVersion = "1.0.0"
            description = "Fast Material 3 Expressive GitHub Client"
            modules("java.instrument", "java.management", "jdk.unsupported", "jdk.crypto.ec")
            linux {
                packageName = "github-mobile-maxxing"
                iconFile.set(project.file("src/desktopMain/resources/app-icon.png"))
                shortcut = true
            }
        }
    }
}

tasks.register<Exec>("packageLinuxAppImage") {
    dependsOn("createDistributable")
    workingDir = rootProject.projectDir
    commandLine("bash", "scripts/package-linux-appimage.sh")
}
