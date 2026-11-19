package dev.maxxing.github.app

import kotlinx.coroutines.CancellationException

/**
 * Runs a session-storage operation (`SessionManager.initialize`, the
 * session save inside sign-in, `signOut`) and turns a thrown storage failure
 * into a [Result] so the UI can show a message instead of crashing.
 *
 * The iOS `TokenStore` throws (for example `errSecInteractionNotAllowed` before
 * the first unlock) without deleting the stored item. Coroutine cancellation is
 * always re-thrown.
 */
internal suspend fun <T> catchingSecureStorage(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

/**
 * The part of a secure-storage failure message that is not translated: the technical detail of
 * [error] in parentheses with a leading space (e.g. ` (OSStatus -25308)`), or an empty string when
 * there is none. The sentence around it comes from the i18n table (`secureStorage*Failed`).
 */
internal fun secureStorageErrorDetail(error: Throwable?): String =
    error?.message?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
