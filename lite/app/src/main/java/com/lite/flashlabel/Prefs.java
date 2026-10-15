package com.lite.flashlabel;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persisted print settings. The screen reads them once on start and writes them all back
 * when the user taps print; the printer address is saved as soon as a connection succeeds.
 */
final class Prefs {
    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("lite", Context.MODE_PRIVATE);
    }

    /** Snapshot of everything the print screen edits. */
    static final class Settings {
        int paperType;
        float widthMm, heightMm;
        int direction; // 1 = horizontal (no rotation), 0 = vertical (rotate 90°)
        int density, speed;
        boolean dither, compress;
        float cropMm; // strip cut off the bottom of every page
    }

    Settings load() {
        Settings s = new Settings();
        s.paperType = sp.getInt("paper", Tspl.PAPER_BLACK_MARK);
        s.widthMm = sp.getFloat("w", 100f);
        s.heightMm = sp.getFloat("h", 150f);
        s.direction = sp.getInt("dir", 1);
        s.density = sp.getInt("density", 8);
        s.speed = sp.getInt("speed", 4);
        s.dither = sp.getBoolean("floyd", false);
        s.compress = sp.getBoolean("compress", true) && Tspl.hasNativeCompressor();
        s.cropMm = sp.getFloat("crop", 0f);
        return s;
    }

    void save(Settings s) {
        sp.edit()
                .putInt("paper", s.paperType)
                .putFloat("w", s.widthMm)
                .putFloat("h", s.heightMm)
                .putInt("dir", s.direction)
                .putInt("density", s.density)
                .putInt("speed", s.speed)
                .putBoolean("floyd", s.dither)
                .putBoolean("compress", s.compress)
                .putFloat("crop", s.cropMm)
                .apply();
    }

    String lastPrinter() {
        return sp.getString("mac", null);
    }

    void lastPrinter(String mac) {
        sp.edit().putString("mac", mac).apply();
    }
}
