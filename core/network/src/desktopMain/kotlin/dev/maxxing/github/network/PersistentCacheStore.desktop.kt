package dev.maxxing.github.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

private class DesktopPersistentCacheStore : PersistentCacheStore {
    private val ioMutex = Mutex()

    private val cacheDir: File by lazy {
        val xdgCache = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }
            ?: "${System.getProperty("user.home")}/.cache"
        File(xdgCache, "github-mobile-maxxing/swr_v1").apply {
            if (!exists()) mkdirs()
            restrictDirPermissions(this)
        }
    }

    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            runCatching {
                val file = File(cacheDir, sanitizeCacheKeyToFileName(key))
                if (file.isFile) file.readText() else null
            }.getOrNull()
        }
    }

    override suspend fun write(key: String, payload: String) {
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                runCatching {
                    val target = File(cacheDir, sanitizeCacheKeyToFileName(key))
                    val tmp = File(cacheDir, "${target.name}.tmp")
                    tmp.writeText(payload)
                    restrictFilePermissions(tmp)
                    try {
                        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    restrictFilePermissions(target)
                }
            }
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                runCatching {
                    cacheDir.listFiles()?.forEach { it.delete() }
                }
            }
        }
    }

    private fun restrictFilePermissions(file: File) {
        runCatching {
            val perms = PosixFilePermissions.fromString("rw-------")
            Files.setPosixFilePermissions(file.toPath(), perms)
        }
    }

    private fun restrictDirPermissions(dir: File) {
        runCatching {
            val perms = setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
            )
            Files.setPosixFilePermissions(dir.toPath(), perms)
        }
    }
}

actual fun createDefaultPersistentCacheStore(): PersistentCacheStore = DesktopPersistentCacheStore()
