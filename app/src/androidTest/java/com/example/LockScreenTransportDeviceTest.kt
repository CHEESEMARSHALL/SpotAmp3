package com.example

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.playback.MusicPlaybackService
import com.example.playback.PlaybackManager
import com.example.playback.TrackItem
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the public media notification and lock-screen transport callbacks with local fixtures. */
@RunWith(AndroidJUnit4::class)
class LockScreenTransportDeviceTest {
    @Test
    fun lockScreenPausePlayNextAndPreviousControlPlayback() {
        assertTrue("Active-notification inspection requires Android 6 or newer", Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val notifications = context.getSystemService(NotificationManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val firstFixture = File(context.cacheDir, "qa-lockscreen-first.wav")
        val secondFixture = File(context.cacheDir, "qa-lockscreen-second.wav")
        writeQuietWav(firstFixture)
        writeQuietWav(secondFixture)
        val first = TrackItem(
            ratingKey = "qa-lockscreen-first",
            title = "QA lock-screen first",
            artist = "QA",
            album = "Generated fixtures",
            key = "/qa/lockscreen-first.wav",
            thumb = "",
            duration = 90_000L,
            localPath = firstFixture.absolutePath
        )
        val second = TrackItem(
            ratingKey = "qa-lockscreen-second",
            title = "QA lock-screen second",
            artist = "QA",
            album = "Generated fixtures",
            key = "/qa/lockscreen-second.wav",
            thumb = "",
            duration = 90_000L,
            localPath = secondFixture.absolutePath
        )

        // A previous fixture run may have left a paused QA media session alive.
        // Replace that session so lock-screen callbacks target this test's player.
        context.stopService(Intent(context, MusicPlaybackService::class.java))
        val manager = PlaybackManager.getInstance(context)

        fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return true
                Thread.sleep(100L)
            }
            return condition()
        }

        fun activeMediaNotification(): Notification? = notifications.activeNotifications
            .firstOrNull { it.id == MusicPlaybackService.NOTIFICATION_ID }
            ?.notification

        fun hasAction(label: String): Boolean = activeMediaNotification()
            ?.actions
            ?.any { it.title?.toString() == label } == true

        fun playbackNotificationReady(): Boolean {
            val notification = activeMediaNotification() ?: return false
            if (!notification.extras.containsKey("android.mediaSession")) return false
            val actionLabels = notification.actions.orEmpty().mapNotNull { it.title?.toString() }
            return listOf(
                context.getString(R.string.notification_action_pause),
                context.getString(R.string.notification_action_previous),
                context.getString(R.string.notification_action_next)
            ).all(actionLabels::contains)
        }

        try {
            instrumentation.runOnMainSync { manager.playTrack(first, listOf(first, second)) }
            assertTrue("QA fixture never reached the playing state", waitFor(10_000L) { manager.isPlaying.value })
            assertTrue(
                "SpotAmp playback media notification did not become ready",
                waitFor(10_000L) { playbackNotificationReady() }
            )

            val playingNotification = activeMediaNotification()
            assertNotNull("SpotAmp media notification disappeared", playingNotification)
            assertEquals("Media notification category", Notification.CATEGORY_TRANSPORT, playingNotification!!.category)
            assertEquals("Lock-screen visibility", Notification.VISIBILITY_PUBLIC, playingNotification.visibility)
            assertTrue("MediaStyle session token is missing", playingNotification.extras.containsKey("android.mediaSession"))
            assertTrue("Pause action is missing", hasAction(context.getString(R.string.notification_action_pause)))
            assertTrue("Previous action is missing", hasAction(context.getString(R.string.notification_action_previous)))
            assertTrue("Next action is missing for a two-track queue", hasAction(context.getString(R.string.notification_action_next)))

            Log.i(TAG, "WAVE5_LOCKSCREEN_READY")
            assertTrue("The phone was not locked for the lock-screen check", waitFor(60_000L) { keyguard.isKeyguardLocked })
            assertTrue("SpotAmp media notification was not retained on the lock screen", activeMediaNotification() != null)
            Log.i(TAG, "WAVE5_LOCKSCREEN_VISIBLE_READY_FOR_PAUSE")

            assertTrue("Lock-screen Pause did not pause SpotAmp", waitFor(30_000L) { !manager.isPlaying.value })
            Log.i(TAG, "WAVE5_LOCKSCREEN_PAUSED")

            assertTrue("Lock-screen Play did not resume SpotAmp", waitFor(30_000L) { manager.isPlaying.value })
            Log.i(TAG, "WAVE5_LOCKSCREEN_PLAYING")

            assertTrue("Lock-screen Next did not advance the queue", waitFor(30_000L) { manager.currentTrack.value?.ratingKey == second.ratingKey })
            Log.i(TAG, "WAVE5_LOCKSCREEN_NEXT")

            manager.pause()
            assertTrue("Could not pause before checking Previous", waitFor(5_000L) { !manager.isPlaying.value })
            manager.seekTo(0L)
            assertTrue("Could not prepare Previous-track check", waitFor(5_000L) { manager.progress.value < 3_000L })
            Log.i(TAG, "WAVE5_LOCKSCREEN_READY_FOR_PREVIOUS")
            assertTrue("Lock-screen Previous did not return to the first track", waitFor(30_000L) { manager.currentTrack.value?.ratingKey == first.ratingKey })
            Log.i(TAG, "WAVE5_LOCKSCREEN_PREVIOUS")
        } finally {
            instrumentation.runOnMainSync {
                manager.pause()
                manager.release()
            }
            context.stopService(Intent(context, MusicPlaybackService::class.java))
            firstFixture.delete()
            secondFixture.delete()
        }
    }

    private fun writeQuietWav(file: File) {
        val sampleRate = 8_000
        val seconds = 90
        val samples = sampleRate * seconds
        val pcm = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val sample = (kotlin.math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 1_200).toInt().toShort()
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
        const val TAG = "LockScreenTransportQa"
    }
}
