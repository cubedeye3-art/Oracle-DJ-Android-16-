package com.example.audio.streaming

enum class StreamPlatform(
    val id: String,
    val displayName: String,
    val shortName: String,
    val defaultIngestUrl: String,
    val defaultBitrateKbps: Int,
    val brandColorHex: Long,
    val tagline: String,
    val setupGuide: String
) {
    YOUTUBE(
        id = "youtube",
        displayName = "YouTube Live",
        shortName = "YouTube",
        defaultIngestUrl = "rtmp://a.rtmp.youtube.com/live2",
        defaultBitrateKbps = 256,
        brandColorHex = 0xFFFF0033,
        tagline = "YouTube Studio Ingest (Ultra-Low Latency)",
        setupGuide = "1. Go to studio.youtube.com\n2. Click 'Go Live' > Stream\n3. Copy your Stream Key and paste below"
    ),
    FACEBOOK(
        id = "facebook",
        displayName = "Facebook Live",
        shortName = "Facebook",
        defaultIngestUrl = "rtmps://live-api-s.facebook.com:443/rtmp/",
        defaultBitrateKbps = 192,
        brandColorHex = 0xFF1877F2,
        tagline = "Facebook Creator Studio / Live Producer",
        setupGuide = "1. Go to facebook.com/live/producer\n2. Select 'Streaming Software'\n3. Copy your Stream Key and paste below"
    ),
    X_TWITTER(
        id = "x_twitter",
        displayName = "X (Twitter)",
        shortName = "X",
        defaultIngestUrl = "rtmps://prod-fastly-us-east-1.video.periscope.tv:443/x",
        defaultBitrateKbps = 192,
        brandColorHex = 0xFFF0F3F5,
        tagline = "X Media Studio Producer Broadcast",
        setupGuide = "1. Open studio.x.com / Media Studio\n2. Navigate to Producer > Create Broadcast\n3. Copy Stream Key from RTMP sources"
    ),
    CUSTOM_RTMP(
        id = "custom_rtmp",
        displayName = "Custom RTMP / Twitch",
        shortName = "Custom",
        defaultIngestUrl = "rtmp://live.twitch.tv/app/",
        defaultBitrateKbps = 256,
        brandColorHex = 0xFF00E5FF,
        tagline = "Twitch / Kick / Restream.io / Custom Server",
        setupGuide = "Enter any RTMP/RTMPS ingest endpoint and stream key for custom streaming destinations."
    )
}
