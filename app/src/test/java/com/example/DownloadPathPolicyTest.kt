package com.example

import com.example.data.DownloadPathPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadPathPolicyTest {
    @Test
    fun preservesNormalServerIdentifier() {
        assertEquals("12345", DownloadPathPolicy.fileStem(" 12345 "))
    }

    @Test
    fun rejectsPathTraversalAndControlCharacters() {
        assertNull(DownloadPathPolicy.fileStem("../outside"))
        assertNull(DownloadPathPolicy.fileStem("folder\\track"))
        assertNull(DownloadPathPolicy.fileStem("track\u0000"))
        assertNull(DownloadPathPolicy.fileStem(".."))
    }
}
