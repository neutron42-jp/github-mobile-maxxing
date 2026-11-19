package dev.maxxing.github.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SecureStorageErrorsTest {

    @Test
    fun returnsSuccessValue() = runTest {
        val result = catchingSecureStorage { 42 }
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun capturesStorageFailureInsteadOfThrowing() = runTest {
        val failure = IllegalStateException("SecItemCopyMatching failed with OSStatus -25308")
        val result = catchingSecureStorage<Unit> { throw failure }
        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }

    @Test
    fun rethrowsCancellation() = runTest {
        assertFailsWith<CancellationException> {
            catchingSecureStorage<Unit> { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun detailIsTheMessageInParentheses() {
        assertEquals(" (OSStatus -25308)", secureStorageErrorDetail(IllegalStateException("OSStatus -25308")))
    }

    @Test
    fun detailIsEmptyWhenThereIsNoMessage() {
        assertEquals("", secureStorageErrorDetail(IllegalStateException("")))
        assertEquals("", secureStorageErrorDetail(IllegalStateException()))
        assertEquals("", secureStorageErrorDetail(null))
    }

    @Test
    fun failureTypeIsPreserved() = runTest {
        val result = catchingSecureStorage<Unit> { error("boom") }
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }
}
