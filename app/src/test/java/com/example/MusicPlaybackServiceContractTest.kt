package com.example

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Looper
import com.example.playback.MusicPlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MusicPlaybackServiceContractTest {
    @Test
    fun createInitializesActiveMediaSessionAndPlaybackChannel() {
        val controller = Robolectric.buildService(MusicPlaybackService::class.java)
        val service = controller.create().get()
        try {
            val mediaSession = MusicPlaybackService::class.java
                .getDeclaredField("mediaSession")
                .apply { isAccessible = true }
                .get(service) as android.media.session.MediaSession
            val notifications = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            assertTrue(mediaSession.isActive)
            assertNotNull(mediaSession.sessionToken)
            assertNotNull(notifications.getNotificationChannel(MusicPlaybackService.CHANNEL_ID))
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun everyStartCommandReentersForegroundAfterNotificationWasDetached() {
        val controller = Robolectric.buildService(MusicPlaybackService::class.java)
        val service = controller.create().get()
        val serviceShadow = shadowOf(service)
        try {
            assertEquals(
                MusicPlaybackService.NOTIFICATION_ID,
                serviceShadow.lastForegroundNotificationId
            )

            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            assertTrue(serviceShadow.isForegroundStopped)

            service.onStartCommand(
                Intent(service, MusicPlaybackService::class.java)
                    .setAction(MusicPlaybackService.ACTION_START),
                0,
                2
            )

            assertFalse(serviceShadow.isForegroundStopped)
            assertEquals(
                MusicPlaybackService.NOTIFICATION_ID,
                serviceShadow.lastForegroundNotificationId
            )
        } finally {
            controller.destroy()
        }
    }
    @Test
    fun servicePlayAndPauseActionsForwardToPlaybackManager() {
        val controller = Robolectric.buildService(MusicPlaybackService::class.java)
        val service = controller.create().get()
        val manager = com.example.playback.PlaybackManager.getInstance(service)
        try {
            service.onStartCommand(Intent(service, MusicPlaybackService::class.java).setAction(MusicPlaybackService.ACTION_PLAY), 0, 1)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(manager.isPlaying.value)

            service.onStartCommand(Intent(service, MusicPlaybackService::class.java).setAction(MusicPlaybackService.ACTION_PAUSE), 0, 2)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(manager.isPlaying.value)
        } finally {
            controller.destroy()
            manager.release()
        }
    }
}
