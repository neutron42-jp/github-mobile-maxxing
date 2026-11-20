package dev.maxxing.github.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.maxxing.github.auth.AndroidAuthContextHolder
import dev.maxxing.github.network.downloadBinaryAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

private const val UPDATES_CACHE_DIR = "updates"

actual object PlatformAppUpdater {
    actual val platformKind: UpdatePlatformKind = UpdatePlatformKind.ANDROID
    actual val supportsInAppInstall: Boolean = true

    actual fun canInstallPackages(): Boolean {
        val context = runCatching { AndroidAuthContextHolder.requireContext() }.getOrNull() ?: return false
        return runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
    }

    actual fun openInstallUnknownAppsSettings(): Boolean {
        val context = runCatching { AndroidAuthContextHolder.requireContext() }.getOrNull() ?: return false
        return runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    actual suspend fun downloadUpdateAsset(
        url: String,
        fileName: String,
        expectedSizeBytes: Long?,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(isTrustedUpdateDownloadUrl(url)) {
                "Untrusted update download URL"
            }
            val context = AndroidAuthContextHolder.requireContext()
            val updatesDir = File(context.cacheDir, UPDATES_CACHE_DIR).apply { mkdirs() }
            // Remove stale APKs / partial downloads from earlier updates before writing a new one.
            updatesDir.listFiles()?.forEach { old ->
                if (old.isFile) old.delete()
            }

            val safeName = fileName.substringAfterLast('/').ifBlank { "update.apk" }
            val tempFile = File(updatesDir, "$safeName.tmp")
            val finalFile = File(updatesDir, safeName)

            try {
                FileOutputStream(tempFile).use { output ->
                    downloadBinaryAsset(
                        url = url,
                        expectedSizeBytes = expectedSizeBytes
                    ) { bytes, length, downloaded, total ->
                        output.write(bytes, 0, length)
                        onProgress(downloaded, total)
                    }
                    output.flush()
                }
                val expected = expectedSizeBytes?.takeIf { it > 0L }
                if (expected != null && tempFile.length() != expected) {
                    throw IllegalStateException(
                        "Incomplete APK download: expected $expected bytes, got ${tempFile.length()}"
                    )
                }
                if (!hasZipSignature(tempFile)) {
                    throw IllegalStateException("Downloaded file is not a valid APK archive")
                }
                verifyDownloadedApkArchive(context, tempFile)
                if (finalFile.exists()) finalFile.delete()
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }
                finalFile.absolutePath
            } catch (t: Throwable) {
                tempFile.delete()
                throw t
            }
        }
    }

    private fun verifyDownloadedApkArchive(context: Context, apkFile: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } ?: throw IllegalStateException("Downloaded APK package metadata could not be parsed")

        if (archiveInfo.packageName != context.packageName) {
            throw IllegalStateException(
                "Downloaded APK package (${archiveInfo.packageName}) does not match ${context.packageName}"
            )
        }

        val installedInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, flags)
        }
        val installedDigests = extractSigningCertDigests(installedInfo)
        val archiveDigests = extractSigningCertDigests(archiveInfo)
        if (installedDigests.isNotEmpty() && archiveDigests.isNotEmpty() &&
            installedDigests.intersect(archiveDigests).isEmpty()
        ) {
            throw IllegalStateException("Downloaded APK signing certificate does not match installed app")
        }
    }

    private fun extractSigningCertDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()
        val md = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig ->
            md.digest(sig.toByteArray()).joinToString("") { b -> "%02x".format(b) }
        }.toSet()
    }

    actual fun installDownloadedPackage(filePath: String): Boolean {
        val context = runCatching { AndroidAuthContextHolder.requireContext() }.getOrNull() ?: return false
        val apkFile = File(filePath)
        if (!apkFile.exists() || !apkFile.isFile) return false
        return runCatching {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun hasZipSignature(file: File): Boolean {
        if (file.length() < 4) return false
        return runCatching {
            file.inputStream().use { input ->
                val b0 = input.read()
                val b1 = input.read()
                b0 == 0x50 && b1 == 0x4B // 'P', 'K'
            }
        }.getOrDefault(false)
    }
}
