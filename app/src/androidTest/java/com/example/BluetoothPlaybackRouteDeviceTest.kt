package com.example

import android.media.AudioDeviceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.playback.PlaybackManager
import com.example.playback.TrackItem
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies a real Bluetooth output route and pause-on-disconnect using a quiet disposable tone. */
@RunWith(AndroidJUnit4::class)
class BluetoothPlaybackRouteDeviceTest {
    @Test
    fun bluetoothRoutePlaysAndPausesWhenHeadsetDisconnects() {
        assertTrue("Media attribute routing queries require Android 13 or newer", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val audioManager = context.getSystemService(AudioManager::class.java)
        val mediaAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val fixture = File(context.cacheDir, "qa-bluetooth-route.wav")
        writeQuietWav(fixture)
        val track = TrackItem(
            ratingKey = "qa-bluetooth-route",
            title = "QA Bluetooth route tone",
            artist = "QA",
            album = "Generated fixtures",
            key = "/qa/bluetooth-route.wav",
            thumb = "",
            duration = 60_000L,
            localPath = fixture.absolutePath
        )
        val manager = PlaybackManager.getInstance(context)

        fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return true
                Thread.sleep(100L)
            }
            return condition()
        }

        fun bluetoothOutputConnected(): Boolean = audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.type in bluetoothOutputTypes }

        fun mediaRouteIsBluetooth(): Boolean = audioManager
            .getAudioDevicesForAttributes(mediaAudioAttributes)
            .any { it.type in bluetoothOutputTypes }

        try {
            assertTrue("Connect the Bluetooth headset as a media-output device before this check", bluetoothOutputConnected())
            instrumentation.runOnMainSync { manager.playTrack(track, listOf(track)) }
            assertTrue("QA tone did not start", waitFor(10_000L) { manager.isPlaying.value })
            assertTrue(
                "SpotAmp media attributes are not routed to a Bluetooth output",
                waitFor(5_000L) { mediaRouteIsBluetooth() }
            )

            Log.i(TAG, "BT_ROUTE_READY_FOR_DISCONNECT")
            val disconnected = waitFor(45_000L) { !bluetoothOutputConnected() }
            assertTrue("Disconnect the headset after SpotAmp confirms the Bluetooth route", disconnected)

            val paused = waitFor(3_000L) { !manager.isPlaying.value }
            assertTrue("Playback continued after the Bluetooth output disconnected", paused)
        } finally {
            instrumentation.runOnMainSync {
                manager.pause()
                manager.release()
            }
            fixture.delete()
        }
    }

    private fun writeQuietWav(file: File) {
        val sampleRate = 8_000
        val seconds = 60
        val samples = sampleRate * seconds
        val pcm = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val sample = (kotlin.math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 700).toInt().toShort()
            pcm[i * 2] = sample.toByte()
            pcm[i * 2 + 1] = (sample.toInt() shr 8).toByte()
        }
        FileOutputStream(file).use { output ->
            fun intLE(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
            fun shortLE(value: Short) = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
            output.write("RIFF".toByteArray())
            output.write(intLE(36 + pcm.size))
            output.write("WAVEfmt ".toByteArray())
            output.write(intLE(16))
            output.write(shortLE(1))
            output.write(shortLE(1))
            output.write(intLE(sampleRate))
            output.write(intLE(sampleRate * 2))
            output.write(shortLE(2))
            output.write(shortLE(16))
            output.write("data".toByteArray())
            output.write(intLE(pcm.size))
            output.write(pcm)
        }
    }

    private companion object {
        const val TAG = "BluetoothRouteQa"
        val bluetoothOutputTypes = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID
        )
    }
}
