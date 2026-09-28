package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.streaming.AVAILABLE_BITRATES
import com.example.audio.streaming.StreamPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Oracle DJ", appName)
    }

    @Test
    fun `verify live stream platform configurations`() {
        // Facebook Live
        val fb = StreamPlatform.FACEBOOK
        assertEquals("Facebook Live", fb.displayName)
        assertTrue(fb.defaultIngestUrl.contains("facebook.com"))

        // YouTube Live
        val yt = StreamPlatform.YOUTUBE
        assertEquals("YouTube Live", yt.displayName)
        assertTrue(yt.defaultIngestUrl.contains("youtube.com"))

        // X (Twitter)
        val x = StreamPlatform.X_TWITTER
        assertEquals("X (Twitter)", x.displayName)
        assertTrue(x.defaultIngestUrl.contains("periscope.tv"))

        // Audio Bitrates
        assertTrue(AVAILABLE_BITRATES.any { it.kbps == 128 })
        assertTrue(AVAILABLE_BITRATES.any { it.kbps == 192 })
        assertTrue(AVAILABLE_BITRATES.any { it.kbps == 256 })
        assertTrue(AVAILABLE_BITRATES.any { it.kbps == 320 })
    }
}

