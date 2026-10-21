package com.example

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LIBRARY_SYNC_NOTIFICATION_CHANNEL_ID
import com.example.data.createLibrarySyncNotification
import com.example.data.ensureLibrarySyncNotificationChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibrarySyncNotificationTest {
    @Test
    fun foregroundNotificationUsesPlainLanguageAndResourceBackedChannel() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        ensureLibrarySyncNotificationChannel(context)
        val notification = createLibrarySyncNotification(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(LIBRARY_SYNC_NOTIFICATION_CHANNEL_ID)

        assertEquals("Updating your music library", notification.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("Saving music details on this phone…", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertNotNull(channel)
        assertEquals("Library updates", channel.name)
    }
}
