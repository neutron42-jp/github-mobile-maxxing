import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
    }
}

fun resolveSetting(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(propName)?.takeIf { it.isNotBlank() }

fun runGitCommand(vararg args: String): String? = runCatching {
    val proc = ProcessBuilder("git", *args)
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val output = proc.inputStream.bufferedReader().readText().trim()
    val exit = proc.waitFor()
    if (exit == 0 && output.isNotEmpty()) output else null
}.getOrNull()

fun resolveGitCommitCount(): Int =
    runGitCommand("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1

fun resolveDefaultMajorMinor(): String {
    val versionRegex = Regex("""^v(\d+\.\d+)\.\d+$""")
    val tags = runGitCommand("tag", "-l", "v[0-9]*.[0-9]*.[0-9]*", "--sort=-v:refname")
        ?.lineSequence()
        .orEmpty()
    return tags.mapNotNull { versionRegex.find(it.trim())?.groupValues?.get(1) }
        .firstOrNull()
        ?: "1.0"
}

val resolvedVersionCode: Int =
    System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull()
        ?: (resolveGitCommitCount() + 100)

val resolvedVersionName: String =
    System.getenv("ANDROID_VERSION_NAME")?.takeIf { it.isNotBlank() }
        ?: "${resolveDefaultMajorMinor()}.${resolveGitCommitCount()}"

val resolvedKeystorePath = resolveSetting("ANDROID_KEYSTORE_PATH", "android.keystore.path")
val resolvedKeystorePassword = resolveSetting("ANDROID_KEYSTORE_PASSWORD", "android.keystore.password")
val resolvedKeyAlias = resolveSetting("ANDROID_KEY_ALIAS", "android.key.alias")
val resolvedKeyPassword = resolveSetting("ANDROID_KEY_PASSWORD", "android.key.password")
val resolvedStoreFile: File? = resolvedKeystorePath?.let { path ->
    val direct = File(path)
    if (direct.isAbsolute && direct.exists()) direct else rootProject.file(path).takeIf { it.exists() }
}
val hasReleaseKeystore = resolvedStoreFile != null &&
    !resolvedKeystorePassword.isNullOrBlank() &&
    !resolvedKeyAlias.isNullOrBlank() &&
    !resolvedKeyPassword.isNullOrBlank()

android {
    namespace = "dev.maxxing.github.app"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "dev.maxxing.github.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = resolvedStoreFile
                storePassword = resolvedKeystorePassword
                keyAlias = resolvedKeyAlias
                keyPassword = resolvedKeyPassword
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
}
