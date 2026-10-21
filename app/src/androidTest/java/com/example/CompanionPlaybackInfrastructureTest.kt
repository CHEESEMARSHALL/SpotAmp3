package com.example

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.PlexSettingsManager
import com.example.playback.PlaybackManager
import com.example.playback.TrackItem
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the supported companion-backed stream through the real PlaybackManager and Media3. */
@RunWith(AndroidJUnit4::class)
class CompanionPlaybackInfrastructureTest {
    @Test
    fun companionStreamReachesPlayingStateWithOriginBoundBearer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val settings = PlexSettingsManager(context)
        val oldUrl = settings.companionBackendUrl
        val oldToken = settings.companionBackendToken
        val oldDeviceId = settings.companionDeviceId
        val oldOfflineOnly = settings.offlineOnly
        val server = LoopbackAudioServer(wavFixture())
        val manager = PlaybackManager.getInstance(context)
        val testOrigin = "http://127.0.0.1:${server.port}"

        try {
            // Clear first so the test cannot enqueue a production-configured sync worker.
            settings.clearCompanionCredential()
            settings.offlineOnly = false
            manager.setCredentials("", "")
            manager.setCompanionToken("COMPANION_TEST_TOKEN_DO_NOT_USE")
            settings.companionBackendUrl = testOrigin
            settings.companionBackendToken = "COMPANION_TEST_TOKEN_DO_NOT_USE"
            settings.companionDeviceId = "qa-device"

            val track = TrackItem(
                ratingKey = "companion:qa-stream-440",
                title = "QA companion tone",
                artist = "SpotAmp QA",
                album = "Companion stream fixture",
                key = "$testOrigin/api/v1/media/stream/qa-stream-440",
                thumb = "",
                duration = 1_000L
            )
            instrumentation.runOnMainSync { manager.playTrack(track, listOf(track)) }

            val deadline = System.currentTimeMillis() + 10_000L
            var observed = false
            while (System.currentTimeMillis() < deadline) {
                if (server.authorization.get() != null && manager.duration.value > 0L && manager.isPlaying.value) {
                    observed = true
                    break
                }
                Thread.sleep(100L)
            }

            assertTrue("Companion stream never reached Media3 playing state or made a loopback request", observed)
            assertEquals("Bearer COMPANION_TEST_TOKEN_DO_NOT_USE", server.authorization.get())
        } finally {
            instrumentation.runOnMainSync {
                manager.pause()
                manager.release()
            }
            settings.clearCompanionCredential()
            if (oldUrl.isNotBlank() && oldToken.isNotBlank()) {
                settings.companionBackendUrl = oldUrl
                settings.companionBackendToken = oldToken
                settings.companionDeviceId = oldDeviceId
            }
            settings.offlineOnly = oldOfflineOnly
            server.close()
        }
    }

    private fun wavFixture(): ByteArray {
        val sampleRate = 8_000
        val samples = sampleRate
        val pcm = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val sample = (sin(2.0 * PI * 440.0 * i / sampleRate) * 8_000).toInt().toShort()
            pcm[i * 2] = sample.toByte()
            pcm[i * 2 + 1] = (sample.toInt() shr 8).toByte()
        }
        return ByteArrayOutputStream().use { output ->
            fun intLe(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
            fun shortLe(value: Short) = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
            output.write("RIFF".toByteArray())
            output.write(intLe(36 + pcm.size))
            output.write("WAVEfmt ".toByteArray())
            output.write(intLe(16))
            output.write(shortLe(1))
            output.write(shortLe(1))
            output.write(intLe(sampleRate))
            output.write(intLe(sampleRate * 2))
            output.write(shortLe(2))
            output.write(shortLe(16))
            output.write("data".toByteArray())
            output.write(intLe(pcm.size))
            output.write(pcm)
            output.toByteArray()
        }
    }

    private class LoopbackAudioServer(private val payload: ByteArray) : AutoCloseable {
        private val socket = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val port: Int get() = socket.localPort
        val authorization = AtomicReference<String?>(null)
        private val worker = thread(start = true, name = "spotamp-companion-fixture") {
            try {
                socket.accept().use { client -> serve(client) }
            } catch (_: Exception) {
                // The test assertion reports a missing playing state or authorization.
            }
        }

        private fun serve(client: Socket) {
            client.soTimeout = 10_000
            val input = client.getInputStream()
            val request = ByteArrayOutputStream()
            var previous = -1
            var current: Int
            while (true) {
                current = input.read()
                if (current == -1) return
                request.write(current)
                if (previous == '\r'.code && current == '\n'.code && request.toByteArray().takeLast(4).toByteArray().contentEquals("\r\n\r\n".toByteArray())) break
                previous = current
            }
            val headers = request.toString(Charsets.ISO_8859_1.name())
            authorization.set(headers.lineSequence().firstOrNull { it.startsWith("Authorization:", true) }?.substringAfter(':')?.trim())
            val response = "HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray()
            client.getOutputStream().use { output ->
                output.write(response)
                output.write(payload)
                output.flush()
            }
        }

        override fun close() {
            socket.close()
            worker.join(2_000L)
        }
    }
}