package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Protects app string resources from leaking provider and protocol implementation details. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ListenerCopyContractTest {
    @Test
    fun stringAndPluralResourcesAvoidImplementationAndRawTransportTerms() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val implementationTerms = Regex(
            """(?i)\bPlex\b|\bbackend\b|\bLocalAI\b|\bLocal AI\b|\bCLAP\b|\btaxonomy\b|\bHTTP\s+[1-5]\d{2}\b|\bexception\b"""
        )
        val resourceValues = mutableListOf<Pair<String, String>>()

        R.string::class.java.fields
            .asSequence()
            .filter { it.type == Int::class.javaPrimitiveType }
            .forEach { field ->
                runCatching { context.getString(field.getInt(null)) }
                    .getOrNull()
                    ?.let { resourceValues += field.name to it }
            }

        R.plurals::class.java.fields
            .asSequence()
            .filter { it.type == Int::class.javaPrimitiveType }
            .forEach { field ->
                val resourceId = field.getInt(null)
                listOf(0, 1, 2).forEach { quantity ->
                    runCatching { context.resources.getQuantityString(resourceId, quantity) }
                        .getOrNull()
                        ?.let { resourceValues += "${field.name}[$quantity]" to it }
                }
            }

        val violations = resourceValues
            .mapNotNull { (name, value) ->
                if (implementationTerms.containsMatchIn(value)) "$name: $value" else null
            }
            .toList()

        assertTrue(
            "Listener-facing app string or plural resources contain implementation or transport terminology:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}
