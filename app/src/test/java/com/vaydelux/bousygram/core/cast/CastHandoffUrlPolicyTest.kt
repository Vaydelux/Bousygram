package com.vaydelux.bousygram.core.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastHandoffUrlPolicyTest {
    private val valid = "http://192.168.1.25:37005/cast/media/" + "a".repeat(24) +
        "?token=" + "b".repeat(43)

    @Test fun acceptsOnlyExactActivePrivateLanStream() {
        assertTrue(CastHandoffUrlPolicy.permits(valid, valid))
        assertFalse(CastHandoffUrlPolicy.permits(valid, null))
        assertFalse(CastHandoffUrlPolicy.permits(valid + "X", valid))
        assertFalse(CastHandoffUrlPolicy.permits(valid, valid + "X"))
    }

    @Test fun rejectsRemotePublicLoopbackAndTamperedRoutes() {
        for (bad in listOf(
            valid.replace("192.168.1.25", "127.0.0.1"),
            valid.replace("192.168.1.25", "8.8.8.8"),
            valid.replace("http://", "https://"),
            valid.replace("192.168.1.25", "example.com"),
            valid.replace("/cast/media/", "/other/media/"),
            valid + "#fragment",
            valid + "&extra=1",
            "http://user:pass@192.168.1.25:37005/cast/media/" + "a".repeat(24) +
                "?token=" + "b".repeat(43)
        )) assertFalse("Unsafe cast URL accepted", CastHandoffUrlPolicy.permits(bad, bad))
    }

    @Test fun supportsAllRfc1918AddressBands() {
        assertTrue(CastHandoffUrlPolicy.permits(valid.replace("192.168.1.25", "10.1.2.3"),
            valid.replace("192.168.1.25", "10.1.2.3")))
        assertTrue(CastHandoffUrlPolicy.permits(valid.replace("192.168.1.25", "172.31.1.5"),
            valid.replace("192.168.1.25", "172.31.1.5")))
        assertFalse(CastHandoffUrlPolicy.permits(valid.replace("192.168.1.25", "172.32.1.5"),
            valid.replace("192.168.1.25", "172.32.1.5")))
    }
}
