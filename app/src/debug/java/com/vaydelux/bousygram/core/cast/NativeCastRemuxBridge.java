package com.vaydelux.bousygram.core.cast;

/** Qualification ABI only. No application call site or automatic library load. */
public final class NativeCastRemuxBridge {
    private NativeCastRemuxBridge() {}

    public interface Callbacks {
        /** Return 1..count bytes at offset; zero/negative means abort, not EOF. */
        int read(long offset, byte[] target, int count);
        /** Consume synchronously; array reused. False aborts conversion. */
        boolean write(byte[] source, int count);
        /** Recheck account/route ownership and cancellation. */
        boolean active();
    }

    /** kind=1 Matroska, kind=2 MPEG-TS. Discard all output unless status=0
     * AND independent media verification passes. IO thread only. */
    public static native int run(int kind, long size, Callbacks callbacks);
}
