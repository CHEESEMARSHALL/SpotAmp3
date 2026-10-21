package com.example

import com.example.playback.TrackItem
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Persists and replays a bounded stress trace for failure investigation. */
class PlaybackStateTraceArtifactTest {
    @Test
    fun traceArtifactCanBeReloadedAndReplayed() {
        val trace = (0 until 500).map { step -> (step * 17 + 3) % 5 }
        val workingDirectory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val repositoryRoot = generateSequence(workingDirectory) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile || File(it, ".git").exists() }
            ?: workingDirectory
        val artifact = File(repositoryRoot, "qa-results/latest/traces/playback-trace.json")
        artifact.parentFile?.mkdirs()
        artifact.writeText(trace.joinToString(prefix = "[", postfix = "]"))

        val reloaded = artifact.readText().removePrefix("[").removeSuffix("]")
            .split(",").mapNotNull { it.trim().toIntOrNull() }
        assertEquals(trace, reloaded)
        assertEquals(execute(trace), execute(reloaded))
        assertTrue("Trace artifact was not created", artifact.isFile)
        println("PLAYBACK_STRESS_TRACE artifact=${artifact.path} replay=PASS count=${reloaded.size}")
    }

    private fun execute(trace: List<Int>): String {
        val tracks = (0 until 40).map { index ->
            TrackItem("artifact-$index", "Track $index", "QA", "Trace", "/artifact/$index", "", 180_000L)
        }
        val queue = tracks.take(5).toMutableList()
        var currentIndex = 0
        var positionMs = 0L
        trace.forEach { action ->
            when (action) {
                0 -> if (queue.size < tracks.size) queue += tracks[queue.size]
                1 -> if (queue.size > 1) queue.removeAt(queue.lastIndex)
                2 -> currentIndex = (currentIndex + 1) % queue.size
                3 -> positionMs = (positionMs + 1_000L).coerceAtMost(86_400_000L)
                4 -> currentIndex = currentIndex.coerceIn(queue.indices)
            }
            currentIndex = currentIndex.coerceIn(queue.indices)
        }
        return "${queue.joinToString { it.ratingKey }}|$currentIndex|$positionMs"
    }
}
