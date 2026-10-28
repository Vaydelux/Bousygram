package org.drinkless.tdlib;

/** Thin JNI boundary for TDLib's official JSON C interface. */
final class TdJsonNative {
    private TdJsonNative() {}
    static native int create();
    static native void send(int clientId, String json);
    static native String receive(double timeoutSeconds);
    static native String execute(String json);
    static native String diagnostics();
}