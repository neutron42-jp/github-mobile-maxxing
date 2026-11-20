package dev.maxxing.github.app.update

import dev.maxxing.github.model.ReleaseAsset
import dev.maxxing.github.model.ReleaseSummary

enum class UpdatePlatformKind {
    ANDROID,
    DESKTOP,
    IOS
}

/**
 * Release channel encoded in an app version.
 *
 * Deployed conventions (see `.github/workflows/release.yml` and `nightly.yml`):
 * - stable releases are tagged `v<major>.<minor>.<patch>`;
 * - nightlies are tagged `nightly` and carry `<major>.<minor>.<commitCount>-nightly.<sha>` in the title;
 * - release candidates use a `-rc.<n>` suffix on the stable numeric triple.
 */
enum class AppVersionChannel(val priority: Int) {
    NIGHTLY(0),
    RC(1),
    STABLE(2)
}

data class AppBuildVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val channel: AppVersionChannel,
    val rcNumber: Int? = null,
    val commitSha: String? = null
) {
    val isNightly: Boolean
        get() = channel == AppVersionChannel.NIGHTLY

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
        when (channel) {
            AppVersionChannel.STABLE -> Unit
            AppVersionChannel.RC -> {
                append("-rc")
                if (rcNumber != null) {
                    append('.')
                    append(rcNumber)
                }
            }
            AppVersionChannel.NIGHTLY -> {
                append("-nightly")
                if (!commitSha.isNullOrBlank()) {
                    append('.')
                    append(commitSha.take(7))
                }
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

private const val NIGHTLY_TAG_NAME = "nightly"
private const val PRODUCT_LABEL = "github-mobile-maxxing"

/**
 * A complete version token. Anchored so that a tag, title, or body fragment cannot
 * contribute an unrelated dependency version.
 */
private val STABLE_VERSION_PATTERN = Regex(
    """^v?(\d+)\.(\d+)(?:\.(\d+))?$""",
    RegexOption.IGNORE_CASE
)
private val RELEASE_CANDIDATE_VERSION_PATTERN = Regex(
    """^v?(\d+)\.(\d+)(?:\.(\d+))?-rc\.?(\d+)$""",
    RegexOption.IGNORE_CASE
)
private val NIGHTLY_VERSION_PATTERN = Regex(
    """^v?(\d+)\.(\d+)(?:\.(\d+))?-nightly(?:\.([0-9a-fA-F]{7,40}))?$""",
    RegexOption.IGNORE_CASE
)

/** Deployed nightly title is `github-mobile-maxxing v<version>-nightly.<sha>`. */
private val NIGHTLY_RELEASE_TITLE_PATTERN = Regex(
    """^(?:$PRODUCT_LABEL\s+)?(v?\d+\.\d+(?:\.\d+)?-nightly(?:\.(?:[0-9a-fA-F]{7,40}))?)$""",
    RegexOption.IGNORE_CASE
)
private val COMMIT_SHA_PATTERN = Regex("""^[0-9a-fA-F]{4,40}$""")

internal fun isNightlyBuildVersion(versionName: String): Boolean =
    versionName.contains("nightly", ignoreCase = true)

private fun normalizeCommitSha(rawCommitSha: String?): String? =
    rawCommitSha?.trim()
        ?.takeIf { COMMIT_SHA_PATTERN.matches(it) }
        ?.lowercase()

/**
 * Parses a complete version token only. Titles such as
 * `github-mobile-maxxing v1.0.89-nightly.8b39263` are not version tokens and return null;
 * release titles are handled by [extractReleaseVersion].
 */
internal fun parseAppBuildVersion(
    rawVersionName: String,
    rawCommitSha: String? = null
): AppBuildVersion? {
    val versionText = rawVersionName.trim()
    if (versionText.isEmpty()) return null
    val fallbackSha = normalizeCommitSha(rawCommitSha)

    NIGHTLY_VERSION_PATTERN.matchEntire(versionText)?.let { match ->
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val patch = match.groupValues[3].ifBlank { "0" }.toIntOrNull() ?: return null
        val tokenSha = normalizeCommitSha(match.groupValues[4])
        return AppBuildVersion(
            major = major,
            minor = minor,
            patch = patch,
            channel = AppVersionChannel.NIGHTLY,
            commitSha = tokenSha ?: fallbackSha
        )
    }

    RELEASE_CANDIDATE_VERSION_PATTERN.matchEntire(versionText)?.let { match ->
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val patch = match.groupValues[3].ifBlank { "0" }.toIntOrNull() ?: return null
        val rcNumber = match.groupValues[4].toIntOrNull() ?: return null
        return AppBuildVersion(
            major = major,
            minor = minor,
            patch = patch,
            channel = AppVersionChannel.RC,
            rcNumber = rcNumber,
            commitSha = fallbackSha
        )
    }

    STABLE_VERSION_PATTERN.matchEntire(versionText)?.let { match ->
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val patch = match.groupValues[3].ifBlank { "0" }.toIntOrNull() ?: return null
        return AppBuildVersion(
            major = major,
            minor = minor,
            patch = patch,
            channel = AppVersionChannel.STABLE,
            commitSha = fallbackSha
        )
    }

    return null
}

private fun sameShaPrefix(a: String, b: String): Boolean =
    if (a.length <= b.length) b.startsWith(a, ignoreCase = true) else a.startsWith(b, ignoreCase = true)

internal fun isSameBuild(current: AppBuildVersion, remote: AppBuildVersion): Boolean {
    if (current.compareNumbersTo(remote) != 0) return false
    if (current.channel != remote.channel) return false
    return when (current.channel) {
        AppVersionChannel.STABLE -> true
        AppVersionChannel.RC -> current.rcNumber == remote.rcNumber
        AppVersionChannel.NIGHTLY -> {
            val currentSha = current.commitSha
            val remoteSha = remote.commitSha
            !currentSha.isNullOrBlank() && !remoteSha.isNullOrBlank() && sameShaPrefix(currentSha, remoteSha)
        }
    }
}

internal fun isRemoteNewer(current: AppBuildVersion, remote: AppBuildVersion): Boolean {
    if (isSameBuild(current, remote)) return false
    val numCmp = remote.compareNumbersTo(current)
    if (numCmp != 0) return numCmp > 0
    // Same numeric triple: a final release supersedes its candidates, and a candidate
    // supersedes a nightly of the same number.
    val channelCmp = remote.channel.priority.compareTo(current.channel.priority)
    if (channelCmp != 0) return channelCmp > 0
    return when (remote.channel) {
        // RC numbers are ordered; different SHAs at the same nightly number have no
        // recorded chronological order, so neither build can claim to be newer.
        AppVersionChannel.RC -> (remote.rcNumber ?: 0) > (current.rcNumber ?: 0)
        AppVersionChannel.STABLE, AppVersionChannel.NIGHTLY -> false
    }
}

internal fun extractReleaseVersion(release: ReleaseSummary): Pair<String, AppBuildVersion>? {
    // The deployed release tag always carries the version for stable and RC releases.
    parseAppBuildVersion(release.tagName)?.let { parsed ->
        return parsed.formatTagLabel() to parsed
    }
    // Nightlies are tagged exactly "nightly"; the workflow puts the version in the title.
    if (!release.tagName.trim().equals(NIGHTLY_TAG_NAME, ignoreCase = true)) return null
    val titleVersion = NIGHTLY_RELEASE_TITLE_PATTERN
        .matchEntire(release.name.trim())
        ?.groupValues
        ?.get(1)
        ?: return null
    val parsed = parseAppBuildVersion(titleVersion) ?: return null
    if (parsed.channel != AppVersionChannel.NIGHTLY) return null
    return parsed.formatTagLabel() to parsed
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
        val asset = findPlatformUpdateAsset(release, platform) ?: return@mapNotNull null
        val (label, parsed) = extractReleaseVersion(release) ?: return@mapNotNull null
        val isPrereleaseUpdate = release.isPrerelease || parsed.channel != AppVersionChannel.STABLE
        if (isPrereleaseUpdate && !includePrereleases) return@mapNotNull null
        if (!isRemoteNewer(current, parsed)) return@mapNotNull null
        AvailableAppUpdate(
            release = release,
            asset = asset,
            versionLabel = label,
            versionKey = "${release.tagName}:$label",
            parsedVersion = parsed,
            isPrerelease = isPrereleaseUpdate
        )
    }
    return candidates.maxWithOrNull(
        Comparator { a, b ->
            val numCmp = a.parsedVersion.compareNumbersTo(b.parsedVersion)
            if (numCmp != 0) return@Comparator numCmp
            // At equal numbers prefer the furthest-along channel. This only chooses which
            // candidate to present; isRemoteNewer decides whether an update exists at all.
            val channelCmp = a.parsedVersion.channel.priority
                .compareTo(b.parsedVersion.channel.priority)
            if (channelCmp != 0) return@Comparator channelCmp
            a.release.publishedAt.compareTo(b.release.publishedAt)
        }
    )
}
