package com.vaydelux.mediaforge.core

import org.junit.Assert.*
import org.junit.Test

class MediaForgeDiscoveryTest {
    @Test fun rejectNonMediaBodiesEvenWhenServerReturnsHttp200() {
        assertFalse(MediaForgeSignature.isPlausible(
            StreamFormat.MP4, "<html>video unavailable</html>".toByteArray()
        ))
        assertFalse(MediaForgeSignature.isPlausible(
            StreamFormat.WEBM, "{\"error\":true}".toByteArray()
        ))
        assertFalse(MediaForgeSignature.isPlausible(
            StreamFormat.MPEG_TS, byteArrayOf(0x00, 0x00)
        ))
        val mp4 = byteArrayOf(0, 0, 0, 24) + "ftyp".toByteArray()
        assertTrue(MediaForgeSignature.isPlausible(StreamFormat.MP4, mp4))
        assertTrue(MediaForgeSignature.isPlausible(
            StreamFormat.WEBM, byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte())
        ))
        assertTrue(MediaForgeSignature.isPlausible(
            StreamFormat.MPEG_TS, byteArrayOf(0x47)
        ))
    }

    @Test fun classificationAndBounds() {
        val registry = MediaForgeDiscovery(maxStreams = 2)
        assertTrue(registry.observe("https://video.example.org/a/master.m3u8?token=secret"))
        assertFalse(registry.observe("https://video.example.org/a/master.m3u8?token=secret"))
        assertTrue(registry.observe("https://video.example.org/subtitles/en.srt"))
        assertTrue(registry.observe("https://video.example.org/one.mp4"))
        assertTrue(registry.observe("https://video.example.org/two.mpd"))
        assertEquals(2, registry.snapshot().streams.size)
        assertEquals(1, registry.snapshot().captions.size)
        registry.clear()
        assertTrue(registry.snapshot().streams.isEmpty())
    }

    @Test fun networkPolicyRejectsPrivateAndReservedAddresses() {
        fun address(value: String) = java.net.InetAddress.getByName(value)
        for (ip in listOf("127.0.0.1", "10.0.0.1", "192.168.1.1", "172.16.0.1",
            "100.64.0.1", "169.254.1.1", "fc00::1", "fe80::1", "::1")) {
            assertFalse("Should block $ip", PublicNetworkPolicy.isPublicAddress(address(ip)))
        }
        assertTrue(PublicNetworkPolicy.isPublicAddress(address("8.8.8.8")))
        assertTrue(PublicNetworkPolicy.isPublicAddress(address("2001:4860:4860::8888")))
    }

    @Test fun playbackTransportHasGuardedDnsAndHttpsRedirects() {
        val client = MediaForgePlaybackTransport.client
        assertTrue(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertEquals(java.net.Proxy.NO_PROXY, client.proxy)
        assertTrue(client.networkInterceptors.isNotEmpty())
        try {
            client.dns.lookup("127.0.0.1")
            fail("Private loopback host must not resolve")
        } catch (_: java.net.UnknownHostException) {
            // Expected: network transport never connects to a private address.
        }
    }

    @Test fun unsafeUrlsRejected() {
        assertNull(MediaForgeClassifier.stream("http://example.org/master.m3u8"))
        assertNull(MediaForgeClassifier.stream("https://127.0.0.1/secret.mp4"))
        assertNull(MediaForgeClassifier.stream("https://localhost/secret.mp4"))
        assertNull(MediaForgeClassifier.stream("https://example.org@localhost/file.mp4"))
    }

    @Test fun mediaQueryStringsAndCaseInsensitiveExtensions() {
        val manifest = MediaForgeClassifier.stream("https://cdn.example.org/MOVIE.M3U8?token=123")
        assertEquals(StreamFormat.HLS, manifest?.format)
        val caption = MediaForgeClassifier.caption("https://cdn.example.org/english.VTT?key=456")
        assertEquals(CaptionFormat.VTT, caption?.format)
        assertNull(MediaForgeClassifier.stream("https://cdn.example.org/fake.m3u8.html"))
    }

    @Test fun maxCaptionsDoNotGrowUnbounded() {
        val collector = MediaForgeDiscovery(maxCaptions = 2)
        for (i in 1..5) assertTrue(collector.observe("https://cdn.example.org/$i.srt"))
        assertEquals(2, collector.snapshot().captions.size)
        assertFalse(collector.observe("https://cdn.example.org/5.srt"))
    }

    @Test fun hlsCaptionsRetainTrackMetadata() {
        val playlist = "#EXTM3U\n#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",NAME=\"English\",LANGUAGE=\"en\",URI=\"sub/en.vtt\""
        val result = MediaForgeHlsCaptions.parse(playlist, "https://media.example.org/master.m3u8").single()
        assertEquals(CaptionFormat.VTT, result.format)
        assertEquals("English", result.label)
        assertEquals("en", result.language)
        assertEquals("https://media.example.org/sub/en.vtt", result.url)
    }

    @Test fun hlsCaptionPlaylist() {
        val playlist = "#EXTM3U\n#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",URI=\"sub/en.vtt\""
        val results = MediaForgeHlsCaptions.parse(playlist, "https://media.example.org/master.m3u8")
        assertEquals("https://media.example.org/sub/en.vtt", results.single().url)
    }
}
