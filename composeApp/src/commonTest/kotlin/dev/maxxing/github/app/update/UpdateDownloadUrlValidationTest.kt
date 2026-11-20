package dev.maxxing.github.app.update

import dev.maxxing.github.model.ReleaseAsset
import dev.maxxing.github.model.ReleaseSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateDownloadUrlValidationTest {

    @Test
    fun isTrustedUpdateDownloadUrlAcceptsOnlyHttpsGitHubHosts() {
        assertTrue(isTrustedUpdateDownloadUrl("https://github.com/neutron42-jp/github-mobile-maxxing/releases/download/v1.0.0/app-release.apk"))
        assertTrue(isTrustedUpdateDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset-123/app.apk"))
        assertFalse(isTrustedUpdateDownloadUrl("http://github.com/neutron42-jp/github-mobile-maxxing/releases/download/v1.0.0/app-release.apk"))
        assertFalse(isTrustedUpdateDownloadUrl("https://user@github.com/app.apk"))
        assertFalse(isTrustedUpdateDownloadUrl("https://evil-github.com/app.apk"))
        assertFalse(isTrustedUpdateDownloadUrl("https://github.com.evil.example/app.apk"))
    }

    @Test
    fun findPlatformUpdateAssetSkipsUntrustedDownloadUrls() {
        val release = ReleaseSummary(
            id = 1L,
            tagName = "v1.0.1",
            name = "v1.0.1",
            assets = listOf(
                ReleaseAsset(id = 10L, name = "app-release.apk", downloadUrl = "https://evil.example.com/app-release.apk"),
                ReleaseAsset(id = 11L, name = "app-trusted.apk", downloadUrl = "https://github.com/octo/app/releases/download/v1.0.1/app-trusted.apk")
            )
        )
        val chosen = findPlatformUpdateAsset(release, UpdatePlatformKind.ANDROID)
        assertEquals(11L, chosen?.id)

        val untrustedOnly = release.copy(assets = listOf(release.assets.first()))
        assertNull(findPlatformUpdateAsset(untrustedOnly, UpdatePlatformKind.ANDROID))
    }
}
