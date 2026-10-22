package com.vaydelux.bousygram.core.tdlib.fastlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FastLinkUrlValidatorTest {
    @Test
    fun acceptsWellFormedHttpsUrl() {
        assertEquals(
            "https://media.example.com/watch?id=123",
            FastLinkUrlValidator.validate("https://media.example.com/watch?id=123")
        )
    }

    @Test
    fun rejectsCleartextHttp() {
        assertNull(FastLinkUrlValidator.validate("http://media.example.com/watch"))
    }

    @Test
    fun rejectsUserInfoAndUnexpectedPorts() {
        assertNull(FastLinkUrlValidator.validate("https://user:secret@media.example.com/watch"))
        assertNull(FastLinkUrlValidator.validate("https://media.example.com:8443/watch"))
    }

    @Test
    fun rejectsMissingHostFragmentsAndMalformedUrls() {
        assertNull(FastLinkUrlValidator.validate("https:///watch"))
        assertNull(FastLinkUrlValidator.validate("https://media.example.com/watch#fragment"))
        assertNull(FastLinkUrlValidator.validate("not a URL"))
    }

    @Test
    fun rejectsControlWhitespaceAndUnknownScheme() {
        assertNull(FastLinkUrlValidator.validate("https://media.example.com/a\nb"))
        assertNull(FastLinkUrlValidator.validate("ftp://media.example.com/video"))
    }
}
