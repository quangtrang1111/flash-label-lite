package com.itpp;

/**
 * JNI shim for libCode941.so (taken from FlashLabel's printer SDK). The library
 * registers its natives on this exact class name, so the package must stay com.itpp.
 */
public final class Code941 {
    static {
        System.loadLibrary("Code941");
    }

    private Code941() {
    }

    /** Compresses a 1-bit raster (bit 1 = black) into BITMAP mode 3 payload. */
    public static native byte[] code(byte[] raster);

    public static native byte[] itppfunction(byte[] data);
}
