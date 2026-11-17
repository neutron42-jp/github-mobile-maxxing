package dev.maxxing.github.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InboxSwipeToDoneTest {
    @Test
    fun markDoneIsHiddenOnlyForAllFilter() {
        assertTrue(isMarkDoneAvailable(InboxFilter.UNREAD))
        assertTrue(isMarkDoneAvailable(InboxFilter.PARTICIPATING))
        assertFalse(isMarkDoneAvailable(InboxFilter.ALL))
    }
}
