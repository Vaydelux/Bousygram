package com.vaydelux.bousygram.core.cast
 
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CastReceiverManagerTest {

    @Before
    fun setUp() {
        CastReceiverManager.clearPendingRequest()
    }

    @Test
    fun serializeAndDeserializeJson_exactMatch() {
        val original = CastMediaPayload(
            chatId = -1001234567890L,
            messageId = 42001L,
            fileId = 98765,
            title = "Big Buck Bunny 1080p",
            positionMs = 125000L,
            durationMs = 600000L,
            thumbnailUrl = "https://example.com/thumb.jpg",
            version = 1
        )

        val jsonString = CastReceiverManager.toJson(original)
        assertNotNull(jsonString)

        val parsed = CastReceiverManager.fromJson(jsonString)
        assertNotNull(parsed)
        assertEquals(original.chatId, parsed!!.chatId)
        assertEquals(original.messageId, parsed.messageId)
        assertEquals(original.fileId, parsed.fileId)
        assertEquals(original.title, parsed.title)
        assertEquals(original.positionMs, parsed.positionMs)
        assertEquals(original.durationMs, parsed.durationMs)
        assertEquals(original.thumbnailUrl, parsed.thumbnailUrl)
        assertEquals(original.version, parsed.version)
    }

    @Test
    fun deserializeCorruptJson_returnsNull() {
        val corrupt = "{\"invalid_json\": true"
        val parsed = CastReceiverManager.fromJson(corrupt)
        assertNull(parsed)
    }

    @Test
    fun deserializeJsonWithMissingFileId_returnsNull() {
        val jsonNoFileId = "{\"chatId\": 1234, \"title\": \"Test\"}"
        val parsed = CastReceiverManager.fromJson(jsonNoFileId)
        assertNull(parsed)
    }

    @Test
    fun serializeAndDeserializeUriString_exactMatch() {
        val original = CastMediaPayload(
            chatId = -100999888777L,
            messageId = 555L,
            fileId = 12345,
            title = "Episode 01 - The Beginning",
            positionMs = 45000L,
            durationMs = 1800000L,
            thumbnailUrl = "https://example.com/thumb2.jpg",
            version = 1
        )

        val uriStr = CastReceiverManager.toUriString(original)
        assertNotNull(uriStr)

        val parsed = CastReceiverManager.fromUriString(uriStr)
        assertNotNull(parsed)
        assertEquals(original.chatId, parsed!!.chatId)
        assertEquals(original.messageId, parsed.messageId)
        assertEquals(original.fileId, parsed.fileId)
        assertEquals(original.title, parsed.title)
        assertEquals(original.positionMs, parsed.positionMs)
        assertEquals(original.durationMs, parsed.durationMs)
        assertEquals(original.thumbnailUrl, parsed.thumbnailUrl)
        assertEquals(original.version, parsed.version)
    }

    @Test
    fun fromUriString_invalidSchemeOrHost_returnsNull() {
        val wrongScheme = "https://google.com/load?fileId=123"
        val parsed = CastReceiverManager.fromUriString(wrongScheme)
        assertNull(parsed)

        val wrongHost = "bousygram://unknown/load?fileId=123"
        val parsedWrongHost = CastReceiverManager.fromUriString(wrongHost)
        assertNull(parsedWrongHost)
    }

    @Test
    fun fromUriString_missingFileId_returnsNull() {
        val missingFileId = "bousygram://cast/load?chatId=123&title=Demo"
        val parsed = CastReceiverManager.fromUriString(missingFileId)
        assertNull(parsed)
    }

    @Test
    fun externalPayloadMustNameARealMessageAndExactDeepLinkPath() {
        assertNull(CastReceiverManager.fromUriString("bousygram://cast/load?fileId=12&chatId=9"))
        assertNull(CastReceiverManager.fromUriString("bousygram://cast/load/extra?fileId=12&chatId=9&messageId=3"))
        assertNull(CastReceiverManager.fromUriString("bousygram://cast-evil/load?fileId=12&chatId=9&messageId=3"))
    }

    @Test
    fun invalidPositionsAndOversizedTitlesAreRejected() {
        assertNull(CastReceiverManager.fromJson("""{"chatId":9,"messageId":3,"fileId":12,"positionMs":-1}"""))
        val oversizedTitle = "x".repeat(257)
        val json = org.json.JSONObject().apply {
            put("chatId", 9)
            put("messageId", 3)
            put("fileId", 12)
            put("title", oversizedTitle)
        }.toString()
        assertNull(CastReceiverManager.fromJson(json))
    }

    @Test
    fun excessiveDurationAndPositionBeyondDurationAreRejected() {
        assertNull(CastReceiverManager.fromJson(
            """{"chatId":9,"messageId":3,"fileId":12,"durationMs":2147483648000}"""
        ))
        assertNull(CastReceiverManager.fromJson(
            """{"chatId":9,"messageId":3,"fileId":12,"durationMs":1000,"positionMs":1001}"""
        ))
    }

    @Test
    fun oversizedJsonIsRejectedBeforeParsing() {
        val json = "{\\"title\\":\\"" + "x".repeat(10_000) + "\\"}"
        assertNull(CastReceiverManager.fromJson(json))
    }

    @Test
    fun toPlaybackRequest_mapsCorrectFields() {
        val payload = CastMediaPayload(
            chatId = 112233L,
            messageId = 445566L,
            fileId = 778899,
            title = "Sintel 4K Remaster",
            positionMs = 30000L,
            durationMs = 900000L
        )

        val streamUrl = "http://127.0.0.1:8080/stream/778899?token=xyz"
        val request = CastReceiverManager.toPlaybackRequest(payload, streamUrl)

        assertEquals(streamUrl, request.initialStreamUrl)
        assertEquals(payload.chatId, request.chatId)
        assertEquals(payload.messageId, request.messageId)
        assertEquals(payload.fileId, request.fileId)
        assertEquals(payload.title, request.title)
        assertEquals(900, request.durationSeconds)
        assertEquals(1, request.queue.size)
        assertEquals(payload.fileId, request.queue.first().fileId)

        val nullRequest = CastReceiverManager.toPlaybackRequest(payload, null)
        assertNull(nullRequest.initialStreamUrl)
    }

    @Test
    fun stateFlow_emitAndClearPendingRequest() {
        val payload = CastMediaPayload(
            chatId = 1L,
            messageId = 2L,
            fileId = 3,
            title = "Test Cast"
        )

        assertNull(CastReceiverManager.pendingCastRequest.value)

        CastReceiverManager.emitCastRequest(payload)
        assertEquals(payload, CastReceiverManager.pendingCastRequest.value)

        CastReceiverManager.clearPendingRequest()
        assertNull(CastReceiverManager.pendingCastRequest.value)
    }
}
