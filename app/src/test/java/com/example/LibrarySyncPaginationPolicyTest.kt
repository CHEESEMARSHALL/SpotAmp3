package com.example

import com.example.data.isPrematureEmptyLibraryPage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySyncPaginationPolicyTest {
    @Test
    fun emptyPageBeforeDeclaredTotalIsRejected() {
        assertTrue(isPrematureEmptyLibraryPage(expectedTotalTrackCount = 1_001, offset = 1_000))
    }

    @Test
    fun emptyPageAtDeclaredEndIsAValidTerminalPage() {
        assertFalse(isPrematureEmptyLibraryPage(expectedTotalTrackCount = 1_000, offset = 1_000))
    }

    @Test
    fun absentTotalDoesNotInventAnExpectedContinuation() {
        assertFalse(isPrematureEmptyLibraryPage(expectedTotalTrackCount = null, offset = 0))
    }
}
