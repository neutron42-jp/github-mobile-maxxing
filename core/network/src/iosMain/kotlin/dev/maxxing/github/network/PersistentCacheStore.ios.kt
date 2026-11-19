@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.maxxing.github.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

private class IosPersistentCacheStore : PersistentCacheStore {
    private val ioMutex = Mutex()

    private fun cacheDirPath(): String? {
        val dirs = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        val base = dirs.firstOrNull() as? String ?: return null
        val path = "$base/maxxing_swr_v1"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        return path
    }

    override suspend fun read(key: String): String? = withContext(Dispatchers.Default) {
        ioMutex.withLock {
            val dir = cacheDirPath() ?: return@withContext null
            val path = "$dir/${sanitizeCacheKeyToFileName(key)}"
            if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return@withContext null
            NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
        }
    }

    override suspend fun write(key: String, payload: String) {
        withContext(Dispatchers.Default) {
            ioMutex.withLock {
                val dir = cacheDirPath() ?: return@withLock
                val path = "$dir/${sanitizeCacheKeyToFileName(key)}"
                @Suppress("CAST_NEVER_SUCCEEDS")
                (payload as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
            }
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.Default) {
            ioMutex.withLock {
                val dir = cacheDirPath() ?: return@withLock
                NSFileManager.defaultManager.removeItemAtPath(dir, error = null)
            }
        }
    }
}

actual fun createDefaultPersistentCacheStore(): PersistentCacheStore = IosPersistentCacheStore()
