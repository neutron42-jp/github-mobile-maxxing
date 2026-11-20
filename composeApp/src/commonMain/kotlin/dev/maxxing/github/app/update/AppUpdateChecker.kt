package dev.maxxing.github.app.update

import dev.maxxing.github.model.ReleaseAsset
import dev.maxxing.github.model.ReleaseSummary

enum class UpdatePlatformKind {
    ANDROID,
    DESKTOP,
    IOS
}

data class AppBuildVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val isNightly: Boolean,
    val commitSha: String?
) {
    fun compareNumbersTo(other: AppBuildVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    fun formatTagLabel(): String = buildString {
        append('v')
        append(major)
        append('.')
        append(minor)
        append('.')
        append(patch)
        if (isNightly) {
            append("-nightly")
            if (!commitSha.isNullOrBlank()) {
                append('.')
                append(commitSha.take(7))
            }
        }
    }
}

data class AvailableAppUpdate(
    val release: ReleaseSummary,
    val asset: ReleaseAsset,
    val versionLabel: String,
    val versionKey: String,
    val parsedVersion: AppBuildVersion,
    val isPrerelease: Boolean
)

private val VERSION_PATTERN = Regex("""v?(\d+)\.(\d+)(?:\.(\d+))?(?:-([0-9A-Za-z._-]+))?""")
private val SHA_SUFFIX_PATTERN = Regex("""(?:^|[._-])([0-9a-fA-F]{7,40})$""")

internal fun isNightlyBuildVersion(versionName: String): Boolean =
    versionName.contains("nightly", ignoreCase = true)

internal fun parseAppBuildVersion(
    rawVersionName: String,
    rawCommitSha: String? = null
): AppBuildVersion? {
    val match = VERSION_PATTERN.find(rawVersionName.trim()) ?: return null
    val major = match.groupValues[1].toIntOrNull() ?: return null
    val minor = match.groupValues[2].toIntOrNull() ?: return null
    val patch = match.groupValues[3].toIntOrNull() ?: 0
    val suffix = match.groupValues[4].takeIf { it.isNotBlank() }
    val isNightly = suffix?.contains("nightly", ignoreCase = true) == true
    val suffixSha = suffix?.let { SHA_SUFFIX_PATTERN.find(it)?.groupValues?.get(1) }
    val normalizedRawSha = rawCommitSha?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
    val commitSha = (suffixSha ?: normalizedRawSha)?.lowercase()
    return AppBuildVersion(
        major = major,
        minor = minor,
        patch = patch,
        isNightly = isNightly,
        commitSha = commitSha
    )
}

internal fun isSameBuild(current: AppBuildVersion, remote: AppBuildVersion): Boolean {
    val curSha = current.commitSha
    val remSha = remote.commitSha
    if (!curSha.isNullOrBlank() && !remSha.isNullOrBlank()) {
        val sameSha = if (curSha.length <= remSha.length) {
            remSha.startsWith(curSha, ignoreCase = true)
        } else {
            curSha.startsWith(remSha, ignoreCase = true)
        }
        if (sameSha) return true
    }
    val numCmp = remote.compareNumbersTo(current)
    if (numCmp == 0 && !current.isNightly && !remote.isNightly) return true
    if (numCmp == 0 && current.isNightly && remote.isNightly && (curSha == null || remSha == null)) {
        return true
    }
    return false
}

internal fun isRemoteNewer(current: AppBuildVersion, remote: AppBuildVersion): Boolean {
    if (isSameBuild(current, remote)) return false
    val numCmp = remote.compareNumbersTo(current)
    if (numCmp > 0) return true
    if (numCmp < 0) return false
    // Same numeric triple (e.g. 1.0.8-nightly -> 1.0.8 stable, or rebuilt nightly at same commit count).
    if (current.isNightly && !remote.isNightly) return true
    if (current.isNightly && remote.isNightly) {
        val curSha = current.commitSha
        val remSha = remote.commitSha
        if (!curSha.isNullOrBlank() && !remSha.isNullOrBlank() && !curSha.equals(remSha, ignoreCase = true)) {
            return true
        }
    }
    return false
}

internal fun extractReleaseVersion(release: ReleaseSummary): Pair<String, AppBuildVersion>? {
    val candidates = if (release.isPrerelease) {
        // Nightly releases use tag "nightly" and embed "v1.0.XX-nightly.<sha>" in the release name/body (Obtainium format).
        listOf(release.name, release.body, release.tagName)
    } else {
        listOf(release.tagName, release.name, release.body)
    }
    for (candidate in candidates) {
        val parsed = parseAppBuildVersion(candidate) ?: continue
        return parsed.formatTagLabel() to parsed
    }
    return null
}

internal fun isTrustedUpdateDownloadUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (!trimmed.startsWith("https://", ignoreCase = true)) return false
    val afterScheme = trimmed.substring("https://".length)
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    if (authority.isBlank() || '@' in authority) return false
    val host = authority.substringBefore(':').lowercase()
    return host == "github.com" || host.endsWith(".githubusercontent.com")
}

internal fun findPlatformUpdateAsset(
    release: ReleaseSummary,
    platform: UpdatePlatformKind
): ReleaseAsset? = when (platform) {
    UpdatePlatformKind.ANDROID -> release.assets.firstOrNull { asset ->
        isTrustedUpdateDownloadUrl(asset.downloadUrl) && (
            asset.name.endsWith(".apk", ignoreCase = true) ||
                asset.contentType.equals("application/vnd.android.package-archive", ignoreCase = true)
            )
    }
    UpdatePlatformKind.DESKTOP -> release.assets.firstOrNull { asset ->
        isTrustedUpdateDownloadUrl(asset.downloadUrl) && (
            asset.name.endsWith(".AppImage", ignoreCase = true) ||
                asset.contentType.equals("application/vnd.appimage", ignoreCase = true)
            )
    }
    UpdatePlatformKind.IOS -> null
}

internal fun selectAvailableUpdate(
    releases: List<ReleaseSummary>,
    currentVersionName: String,
    currentCommitSha: String,
    includePrereleases: Boolean,
    platform: UpdatePlatformKind
): AvailableAppUpdate? {
    val current = parseAppBuildVersion(currentVersionName, currentCommitSha) ?: return null
    val candidates = releases.mapNotNull { release ->
        if (release.isDraft) return@mapNotNull null
        if (release.isPrerelease && !includePrereleases) return@mapNotNull null
        val asset = findPlatformUpdateAsset(release, platform) ?: return@mapNotNull null
        val (label, parsed) = extractReleaseVersion(release) ?: return@mapNotNull null
        if (!isRemoteNewer(current, parsed)) return@mapNotNull null
        AvailableAppUpdate(
            release = release,
            asset = asset,
            versionLabel = label,
            versionKey = "${release.tagName}:$label",
            parsedVersion = parsed,
            isPrerelease = release.isPrerelease
        )
    }
    return candidates.maxWithOrNull(
        Comparator { a, b ->
            val numCmp = a.parsedVersion.compareNumbersTo(b.parsedVersion)
            if (numCmp != 0) return@Comparator numCmp
            // Prefer stable release over nightly at equal numeric version.
            val stableCmp = (!a.isPrerelease).compareTo(!b.isPrerelease)
            if (stableCmp != 0) return@Comparator stableCmp
            a.release.publishedAt.compareTo(b.release.publishedAt)
        }
    )
}
