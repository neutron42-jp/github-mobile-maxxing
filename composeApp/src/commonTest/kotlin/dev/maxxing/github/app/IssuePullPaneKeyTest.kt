package dev.maxxing.github.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IssuePullPaneKeyTest {
    @Test
    fun roundTripsOwnerRepoAndNumber() {
        listOf(
            IssuePullTarget("hubot", "app", 19),
            IssuePullTarget("my-org", "repo.name_v2-x", 1),
            IssuePullTarget("user_name", ".github", 12345),
            IssuePullTarget("a-b", "dotted.repo.name", Int.MAX_VALUE)
        ).forEach { target ->
            val key = target.toPaneKey()
            assertEquals(target, issuePullTargetFromPaneKey(key), "key=$key")
        }
    }

    @Test
    fun keyIgnoresHintTitleAndDecodesWithoutIt() {
        val withHint = IssuePullTarget("my-org", "repo.name", 7, hintTitle = "Fix #3 / crash")
        val withoutHint = IssuePullTarget("my-org", "repo.name", 7)

        assertEquals("my-org/repo.name#7", withHint.toPaneKey())
        assertEquals(withoutHint.toPaneKey(), withHint.toPaneKey())
        val decoded = issuePullTargetFromPaneKey(withHint.toPaneKey())
        assertEquals(withoutHint, decoded)
        assertNull(decoded?.hintTitle)
    }

    @Test
    fun malformedKeysDecodeToNull() {
        listOf(
            "",
            "owner/repo",
            "owner#1",
            "/repo#1",
            "owner/#1",
            "owner/repo#",
            "owner/repo#abc",
            "owner/repo#0",
            "owner/repo#-1",
            "a/b/c#1",
            "owner/repo#99999999999"
        ).forEach { key ->
            assertNull(issuePullTargetFromPaneKey(key), "key=$key")
        }
    }
}
