package dev.maxxing.github.app.update

import dev.maxxing.github.model.ReleaseAsset
import dev.maxxing.github.model.ReleaseSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdateCheckerTest {

    private fun sampleRelease(
        tagName: String,
        name: String,
        isPrerelease: Boolean,
        isDraft: Boolean = false,
        publishedAt: String = "2026-11-19T00:00:00Z",
        apkName: String = "github-mobile-maxxing-release.apk",
        appImageName: String = "github-mobile-maxxing-x86_64.AppImage"
    ): ReleaseSummary = ReleaseSummary(
        id = 1L,
        tagName = tagName,
        name = name,
        isDraft = isDraft,
        isPrerelease = isPrerelease,
        publishedAt = publishedAt,
        assets = listOf(
            ReleaseAsset(
                id = 10L,
                name = apkName,
                sizeBytes = 3_200_000L,
                downloadUrl = "https://github.com/neutron42-jp/github-mobile-maxxing/releases/download/$tagName/$apkName",
                contentType = "application/vnd.android.package-archive"
            ),
            ReleaseAsset(
                id = 11L,
                name = appImageName,
                sizeBytes = 79_000_000L,
                downloadUrl = "https://github.com/neutron42-jp/github-mobile-maxxing/releases/download/$tagName/$appImageName",
                contentType = "application/vnd.appimage"
            )
        ),
        htmlUrl = "https://github.com/neutron42-jp/github-mobile-maxxing/releases/tag/$tagName"
    )

    @Test
    fun parsesStableNightlyAndPrBuildVersions() {
        assertFalse(isNightlyBuildVersion("1.0.8"))
        assertTrue(isNightlyBuildVersion("1.0.89-nightly.8b39263"))

        val stable = assertNotNull(parseAppBuildVersion("v1.0.8", "f075a64"))
        assertEquals(1, stable.major)
        assertEquals(0, stable.minor)
        assertEquals(8, stable.patch)
        assertFalse(stable.isNightly)
        assertEquals("f075a64", stable.commitSha)

        val nightly = assertNotNull(parseAppBuildVersion("github-mobile-maxxing v1.0.89-nightly.8b39263"))
        assertEquals(1, nightly.major)
        assertEquals(0, nightly.minor)
        assertEquals(89, nightly.patch)
        assertTrue(nightly.isNightly)
        assertEquals("8b39263", nightly.commitSha)
        assertEquals("v1.0.89-nightly.8b39263", nightly.formatTagLabel())
    }

    @Test
    fun stableBuildIgnoresPrereleasesUnlessIncluded() {
        val nightly = sampleRelease(
            tagName = "nightly",
            name = "github-mobile-maxxing v1.0.89-nightly.8b39263",
            isPrerelease = true,
            apkName = "github-mobile-maxxing-nightly.apk"
        )
        val stable = sampleRelease(
            tagName = "v1.0.8",
            name = "github-mobile-maxxing v1.0.8",
            isPrerelease = false
        )
        val newerStable = sampleRelease(
            tagName = "v1.0.9",
            name = "github-mobile-maxxing v1.0.9",
            isPrerelease = false
        )

        // With prereleases OFF, stable 1.0.8 sees neither 1.0.8 nor nightly.
        assertNull(
            selectAvailableUpdate(
                releases = listOf(nightly, stable),
                currentVersionName = "1.0.8",
                currentCommitSha = "f075a64",
                includePrereleases = false,
                platform = UpdatePlatformKind.ANDROID
            )
        )

        // With prereleases OFF, newer stable v1.0.9 is selected.
        val stableUpdate = assertNotNull(
            selectAvailableUpdate(
                releases = listOf(nightly, newerStable, stable),
                currentVersionName = "1.0.8",
                currentCommitSha = "f075a64",
                includePrereleases = false,
                platform = UpdatePlatformKind.ANDROID
            )
        )
        assertEquals("v1.0.9", stableUpdate.versionLabel)
        assertEquals("github-mobile-maxxing-release.apk", stableUpdate.asset.name)

        // With prereleases ON, nightly 1.0.89 is selected over 1.0.8.
        val nightlyUpdate = assertNotNull(
            selectAvailableUpdate(
                releases = listOf(nightly, stable),
                currentVersionName = "1.0.8",
                currentCommitSha = "f075a64",
                includePrereleases = true,
                platform = UpdatePlatformKind.ANDROID
            )
        )
        assertEquals("v1.0.89-nightly.8b39263", nightlyUpdate.versionLabel)
        assertEquals("nightly:v1.0.89-nightly.8b39263", nightlyUpdate.versionKey)
        assertEquals("github-mobile-maxxing-nightly.apk", nightlyUpdate.asset.name)
    }

    @Test
    fun nightlyBuildDetectsNewerNightlySkipsSelfAndAvoidsDowngradeToOlderStable() {
        val currentNightly = sampleRelease(
            tagName = "nightly",
            name = "github-mobile-maxxing v1.0.90-nightly.aaaa111",
            isPrerelease = true,
            apkName = "github-mobile-maxxing-nightly.apk"
        )
        val olderStable = sampleRelease(
            tagName = "v1.0.8",
            name = "github-mobile-maxxing v1.0.8",
            isPrerelease = false
        )
        val newerNightly = sampleRelease(
            tagName = "nightly",
            name = "github-mobile-maxxing v1.0.91-nightly.bbbb222",
            isPrerelease = true,
            apkName = "github-mobile-maxxing-nightly.apk"
        )

        // Same commit SHA on nightly -> already up to date.
        assertNull(
            selectAvailableUpdate(
                releases = listOf(currentNightly, olderStable),
                currentVersionName = "1.0.90-nightly.aaaa111",
                currentCommitSha = "aaaa111",
                includePrereleases = true,
                platform = UpdatePlatformKind.ANDROID
            )
        )

        // Turning prereleases OFF on 1.0.90-nightly does NOT downgrade to v1.0.8.
        assertNull(
            selectAvailableUpdate(
                releases = listOf(currentNightly, olderStable),
                currentVersionName = "1.0.90-nightly.aaaa111",
                currentCommitSha = "aaaa111",
                includePrereleases = false,
                platform = UpdatePlatformKind.ANDROID
            )
        )

        // PR test APK (1.0.90, commit cccc333) updates cleanly to newer Nightly 1.0.91-nightly.bbbb222.
        val fromPrToNightly = assertNotNull(
            selectAvailableUpdate(
                releases = listOf(newerNightly, olderStable),
                currentVersionName = "1.0.90",
                currentCommitSha = "cccc333",
                includePrereleases = true,
                platform = UpdatePlatformKind.DESKTOP
            )
        )
        assertEquals("v1.0.91-nightly.bbbb222", fromPrToNightly.versionLabel)
        assertEquals("github-mobile-maxxing-x86_64.AppImage", fromPrToNightly.asset.name)
    }

    @Test
    fun minorReleaseDetectsNextMinorNightlyWhenIncluded() {
        val nextNightly = sampleRelease(
            tagName = "nightly",
            name = "github-mobile-maxxing v1.1.110-nightly.dddd444",
            isPrerelease = true,
            apkName = "github-mobile-maxxing-nightly.apk"
        )
        val currentStable = sampleRelease(
            tagName = "v1.1.0",
            name = "github-mobile-maxxing v1.1.0",
            isPrerelease = false
        )

        val update = assertNotNull(
            selectAvailableUpdate(
                releases = listOf(nextNightly, currentStable),
                currentVersionName = "1.1.0",
                currentCommitSha = "73fcc3d",
                includePrereleases = true,
                platform = UpdatePlatformKind.ANDROID
            )
        )
        assertEquals("v1.1.110-nightly.dddd444", update.versionLabel)
        assertEquals("github-mobile-maxxing-nightly.apk", update.asset.name)

        // With prereleases OFF, stable 1.1.0 ignores the 1.1.x nightly.
        assertNull(
            selectAvailableUpdate(
                releases = listOf(nextNightly, currentStable),
                currentVersionName = "1.1.0",
                currentCommitSha = "73fcc3d",
                includePrereleases = false,
                platform = UpdatePlatformKind.ANDROID
            )
        )
    }
}
