package dev.maxxing.github.app.ui

import dev.maxxing.github.app.IssuePullTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UiStateGuardsTest {

    @Test
    fun issuePullIdentityKeyIgnoresHintTitleAndCase() {
        val first = IssuePullTarget("Octo", "App", 42, hintTitle = "Initial title")
        val updatedHint = IssuePullTarget("octo", "app", 42, hintTitle = "Updated title")
        val withoutHint = IssuePullTarget("OCTO", "APP", 42, hintTitle = null)
        val otherNumber = IssuePullTarget("Octo", "App", 43, hintTitle = "Initial title")
        val otherRepo = IssuePullTarget("Octo", "Other", 42, hintTitle = "Initial title")

        assertEquals(issuePullIdentityKey(first), issuePullIdentityKey(updatedHint))
        assertEquals(issuePullIdentityKey(first), issuePullIdentityKey(withoutHint))
        assertNotEquals(issuePullIdentityKey(first), issuePullIdentityKey(otherNumber))
        assertNotEquals(issuePullIdentityKey(first), issuePullIdentityKey(otherRepo))
    }

    @Test
    fun latestRequestGuardMarksOnlyMostRecentTokenAsLatest() {
        val guard = LatestRequestGuard()
        val first = guard.next()
        assertTrue(guard.isLatest(first))

        val second = guard.next()
        assertFalse(guard.isLatest(first))
        assertTrue(guard.isLatest(second))

        val third = guard.next()
        assertFalse(guard.isLatest(first))
        assertFalse(guard.isLatest(second))
        assertTrue(guard.isLatest(third))
    }
}
