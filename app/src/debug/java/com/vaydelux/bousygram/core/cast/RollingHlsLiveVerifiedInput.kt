package com.vaydelux.bousygram.core.cast

import java.io.IOException
import java.io.InputStream

/** Adapter to the existing strict framing and sample-table verifier. Owns the
 * input stream; no decoder or native producer is invoked by this adapter. */
internal class RollingHlsLiveVerifiedInput(
    private val input: InputStream,
    private val permitted: () -> Boolean
) : RollingHlsLiveProducer.VerifiedInput {
    private val framing = RollingFmp4Framing(input, permitted)
    private val verifier = RollingFmp4SampleVerifier()
    private var header: ByteArray? = null
    private var closed = false
    private fun access() { if (closed || !permitted()) throw IOException("Verified live input revoked") }

    override fun initialization(): ByteArray {
        access()
        check(header == null)
        val bytes = framing.readInit()
        try {
            if (!verifier.verifyInit(bytes)) throw IOException("Live init verification failed")
            access()
            header = bytes
            return bytes.copyOf()
        } catch (error: Exception) { bytes.fill(0); throw error }
    }

    override fun next(): RollingHlsLiveProducer.Unit? {
        access()
        val init = checkNotNull(header)
        val fragment = framing.readFragment() ?: return null
        try {
            val timing = verifier.verifyFragment(init, fragment.sequence, fragment.bytes)
                ?: throw IOException("Live sample verification failed")
            access()
            return RollingHlsLiveProducer.Unit(fragment.sequence, timing.startUs, timing.durationUs, fragment.bytes)
        } catch (error: Exception) { fragment.bytes.fill(0); throw error }
    }

    override fun close() {
        if (closed) return
        closed = true
        header?.fill(0)
        header = null
        input.close()
    }
}
