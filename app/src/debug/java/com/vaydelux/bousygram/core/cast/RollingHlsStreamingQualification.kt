package com.vaydelux.bousygram.core.cast

import java.io.IOException

/**
 * Finite, direct native-to-rolling-store qualification, with no converted
 * staging file. Native input/output/time caps are unchanged. Independent
 * source packet conservation and decoder verification remain separate gates.
 */
internal object RollingHlsStreamingQualification {
    fun convert(kind: Int, source: RollingHlsSource, store: RollingHlsSegmentStore,
                permitted: () -> Boolean, admit: (Long) -> Boolean,
                acknowledge: (RollingHlsSegmentStore.Segment) -> Boolean,
                verifier: RollingHlsVerifiedFeed.Verifier = RollingFmp4SampleVerifier(),
                invoke: (Int, Long, NativeCastRemuxBridge.Callbacks) -> Int = NativeCastRemuxBridge::run
    ): RollingHlsStreamingSink {
        val sink = RollingHlsStreamingSink(store, permitted, verifier, acknowledge,
            { System.nanoTime() / 1_000_000L })
        var transferred = false
        try {
            if (!permitted() || !admit(0)) throw IOException("Streaming admission denied")
            // JNI callback admission is deliberately conservative: count the
            // incoming bytes even while they are only in memory. Store writes
            // independently require fresh shared storage admission.
            val outcome = RollingHlsNativeCallbacks(source, sink, permitted, admit).use { callbacks ->
                callbacks.execute(kind, invoke)
            }
            if (!outcome.transferComplete || !permitted() || !admit(0))
                throw IOException("Native streaming transfer failed")
            sink.finish()
            transferred = true
            return sink
        } finally {
            if (!transferred) {
                source.close()
                sink.close()
            }
        }
    }
}
