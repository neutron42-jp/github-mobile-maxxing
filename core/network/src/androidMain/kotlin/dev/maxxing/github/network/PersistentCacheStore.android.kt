package dev.maxxing.github.network

import dev.maxxing.github.auth.AndroidAuthContextHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private class AndroidPersistentCacheStore : PersistentCacheStore {
    private val ioMutex = Mutex()

    private fun cacheDirOrNull(): File? = runCatching {
        val base = AndroidAuthContextHolder.requireContext().cacheDir
        File(base, "maxxing_swr_v1").apply { if (!exists()) mkdirs() }
    }.getOrNull()

    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            runCatching {
                val dir = cacheDirOrNull() ?: return@withContext null
                val file = File(dir, sanitizeCacheKeyToFileName(key))
                if (file.isFile) file.readText() else null
            }.getOrNull()
        }
    }

    override suspend fun write(key: String, payload: String) {
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                runCatching {
                    val dir = cacheDirOrNull() ?: return@withContext
                    val target = File(dir, sanitizeCacheKeyToFileName(key))
                    val tmp = File(dir, "${target.name}.tmp")
                    tmp.writeText(payload)
                    if (!tmp.renameTo(target)) {
                        target.delete()
                        if (!tmp.renameTo(target)) {
                            tmp.delete()
                        }
                    }
                }
            }
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                runCatching {
                    cacheDirOrNull()?.listFiles()?.forEach { it.delete() }
                }
            }
        }
    }
}

actual fun createDefaultPersistentCacheStore(): PersistentCacheStore = AndroidPersistentCacheStore()
