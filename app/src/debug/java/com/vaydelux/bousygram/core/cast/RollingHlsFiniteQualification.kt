package com.vaydelux.bousygram.core.cast

import java.io.File
import java.io.IOException
import java.io.FileInputStream

/**
 * Debug-only finite JNI-to-segment qualification. No native library loading,
 * live account access or receiver delivery. The caller supplies a synthetic
 * source, private empty directory and independent verifier.
 *
 * Stages at most 20 MiB of converted output before feed admission. This is NOT
 * a continuous rolling producer: source still limited to 16 MiB. All staged
 * bytes and segment copies must count in the same storage measurement.
 */
internal object RollingHlsFiniteQualification {
    class Artifact internal constructor(private val file: File,
                                        private val feed: RollingHlsVerifiedFeed) : AutoCloseable {
        fun step(): RollingHlsVerifiedFeed.Step = feed.step()
        fun initialization(): ByteArray? = feed.initialization()

        override fun close() {
            try { feed.close() } finally {
                if (file.exists() && !file.delete()) throw IOException("Qualification scratch cleanup failed")
            }
        }
    }

    fun convert(kind: Int, source: RollingHlsSource, directory: File,
                store: RollingHlsSegmentStore, permitted: () -> Boolean, admit: (Long) -> Boolean,
                verifier: RollingHlsVerifiedFeed.Verifier,
                invoke: (Int, Long, NativeCastRemuxBridge.Callbacks) -> Int = NativeCastRemuxBridge::run): Artifact {
        require(directory.isDirectory && directory.listFiles()?.isEmpty() == true)
        if (!permitted() || !admit(0)) throw IOException("Qualification admission denied")
        val staged = File.createTempFile("native-", ".part", directory)
        var handedOff = false
        try {
            val outcome = staged.outputStream().use { sink ->
                RollingHlsNativeCallbacks(source, sink, permitted, admit).use { callbacks ->
                    callbacks.execute(kind, invoke)
                }
            }
            if (!outcome.transferComplete || !permitted() || !admit(0) ||
                staged.length() != outcome.outputBytes || staged.length() !in 1L..(20L * 1024 * 1024))
                throw IOException("Native transfer did not qualify")
            val input = FileInputStream(staged)
            val result = Artifact(staged, RollingHlsVerifiedFeed(input, store, permitted, verifier))
            handedOff = true
            return result
        } finally {
            if (!handedOff) {
                source.close()
                try { store.close() } finally {
                    if (staged.exists() && !staged.delete()) throw IOException("Qualification scratch cleanup failed")
                }
            }
        }
    }
}
