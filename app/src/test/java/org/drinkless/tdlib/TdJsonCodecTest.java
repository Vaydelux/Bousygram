package org.drinkless.tdlib;

import org.junit.Test;
import static org.junit.Assert.*;

public class TdJsonCodecTest {
    @Test public void preservesLongIds() {
        TdApi.DownloadFile request = new TdApi.DownloadFile();
        request.offset = Long.MAX_VALUE;
        String json = TdJsonCodec.encode(request, "42");
        assertTrue(json.contains("\"offset\":\"9223372036854775807\""));
        assertTrue(json.contains("\"@extra\":\"42\""));
    }
    @Test public void rejectsUnknownConstructor() {
        try {
            TdJsonCodec.decodeEnvelope("{\"@type\":\"notARealTdlibType\",\"@extra\":\"1\"}");
            fail("Unknown response must not be silently accepted");
        } catch (TdJsonCodec.CodecException expected) {
            assertTrue(expected.getMessage().contains("Unknown TDLib constructor"));
        }
    }
    @Test public void decodesErrorAndCorrelation() {
        TdJsonCodec.Envelope result = TdJsonCodec.decodeEnvelope(
            "{\"@type\":\"error\",\"code\":400,\"message\":\"Bad request\",\"@extra\":\"9007199254740993\",\"@client_id\":1}");
        assertEquals("9007199254740993", result.extra);
        assertEquals(1,result.clientId);
        assertTrue(result.object instanceof TdApi.Error);
        assertEquals(400,((TdApi.Error)result.object).code);
    }
}