package com.example

import com.example.data.fetchOptionalMusicIntelligencePage
import com.squareup.moshi.JsonDataException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LibrarySyncOptionalEnrichmentTest {
    @Test
    fun malformedOptionalPageDoesNotFailCoreLibrarySync() = runTest {
        val failures = mutableListOf<Exception>()

        val result: String? = fetchOptionalMusicIntelligencePage(
            fetch = { throw JsonDataException("Invalid optional page") },
            onFailure = failures::add
        )

        assertNull(result)
        assertEquals(1, failures.size)
        assertEquals(JsonDataException::class.java, failures.single()::class.java)
    }

    @Test
    fun cancellationStillPropagatesInsteadOfBecomingAnOptionalFailure() = runTest {
        val cancellation = CancellationException("Sync cancelled")

        val result = runCatching {
            fetchOptionalMusicIntelligencePage<String>(
                fetch = { throw cancellation },
                onFailure = { error -> throw AssertionError("Cancellation was swallowed: $error") }
            )
        }

        assertSame(cancellation, result.exceptionOrNull())
    }
}
