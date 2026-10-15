package com.lite.flashlabel;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

import com.itpp.Code941;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Label rendering + TSPL command building. Mirrors com.itpp.b / com.itpp.c / com.itpp.e
 * and PrintTask from the original app.
 */
final class Tspl {
    static final int DOTS_PER_MM = 8; // 200 dpi: px = mm * 200 / 25
    static final int THRESHOLD = 175; // DeviceManager.threshold

    static final int PAPER_CONTINUOUS = 0, PAPER_GAP = 1, PAPER_BLACK_MARK = 2;

    private static Boolean nativeOk;

    private Tspl() {
    }

    static synchronized boolean hasNativeCompressor() {
        if (nativeOk == null) {
            try {
                Class.forName("com.itpp.Code941");
                nativeOk = true;
            } catch (Throwable t) {
                nativeOk = false;
            }
        }
        return nativeOk;
    }

    /**
     * Same as PrintTask.doInBackground: white canvas of the label size in dots,
     * source rotated by {@code angle} then scaled to fit and centered.
     */
    static Bitmap renderLabel(Bitmap src, int widthMm, int heightMm, float angle, boolean dither) {
        int w = widthMm * DOTS_PER_MM, h = heightMm * DOTS_PER_MM;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.WHITE);
        Bitmap s = src;
        if (angle != 0f) {
            Matrix r = new Matrix();
            r.postRotate(angle);
            s = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), r, true);
        }
        float scale = Math.min(w / (float) s.getWidth(), h / (float) s.getHeight());
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        m.postTranslate((w - (int) (s.getWidth() * scale)) >> 1, (h - (int) (s.getHeight() * scale)) >> 1);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(s, m, p);
        if (s != src) s.recycle();
        if (dither) floydSteinberg(out);
        return out;
    }

    /**
     * Drops a horizontal strip off the bottom of a page.
     * @param fraction share of the height to remove (0 = keep all); returns {@code src} when nothing is cut
     */
    static Bitmap cropBottom(Bitmap src, float fraction) {
        if (fraction <= 0f) return src;
        int keep = Math.max(1, Math.round(src.getHeight() * (1f - fraction)));
        if (keep >= src.getHeight()) return src;
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), keep);
    }

    /** Replaces OpenCVUtils.getFlyodBitmap (19 MB of OpenCV) with plain Floyd–Steinberg. */
    static void floydSteinberg(Bitmap bmp) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        float[] cur = new float[w + 2], next = new float[w + 2];
        for (int x = 0; x < w; x++) cur[x + 1] = luma(px[x]);
        for (int y = 0; y < h; y++) {
            if (y + 1 < h) for (int x = 0; x < w; x++) next[x + 1] = luma(px[(y + 1) * w + x]);
            for (int x = 0; x < w; x++) {
                float old = cur[x + 1];
                int v = old < 128 ? 0 : 255;
                float err = old - v;
                cur[x + 2] += err * 7 / 16;
                next[x] += err * 3 / 16;
                next[x + 1] += err * 5 / 16;
                next[x + 2] += err / 16;
                px[y * w + x] = v == 0 ? Color.BLACK : Color.WHITE;
            }
            float[] t = cur;
            cur = next;
            next = t;
            java.util.Arrays.fill(next, 0f);
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static float luma(int c) {
        return Color.red(c) * 0.3f + Color.green(c) * 0.59f + Color.blue(c) * 0.11f;
    }

    /** c.b(): avg(RGB) &lt; threshold -&gt; bit set (black). Input for Code941. */
    private static byte[] packBlackBits(Bitmap bmp, int thr) {
        int w = bmp.getWidth(), h = bmp.getHeight(), wb = (w + 7) / 8;
        byte[] out = new byte[wb * h];
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            bmp.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                int c = row[x];
                if (c == -1) continue;
                if ((Color.red(c) + Color.green(c) + Color.blue(c)) / 3 < thr) out[y * wb + (x >> 3)] |= (byte) (0x80 >>> (x & 7));
            }
        }
        return out;
    }

    /** c.a(bmp,w,h,thr): bit set = white (0 burns). Uncompressed BITMAP mode 1. */
    private static byte[] packWhiteBits(Bitmap bmp, int thr) {
        int w = bmp.getWidth(), h = bmp.getHeight(), wb = (w + 7) / 8;
        byte[] out = new byte[wb * h];
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            bmp.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                int c = row[x];
                if (c == -1 || (int) luma(c) >= thr) out[y * wb + (x >> 3)] |= (byte) (0x80 >>> (x & 7));
            }
        }
        return out;
    }

    /** BITMAP command incl. trailing CRLFs (c.a(z=true/false,...) for family 1). */
    static byte[] bitmapCommand(Bitmap bmp, boolean compressed) {
        int wb = (bmp.getWidth() + 7) / 8, h = bmp.getHeight();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        if (compressed) {
            byte[] data = Code941.code(packBlackBits(bmp, THRESHOLD));
            ascii(bo, "BITMAP 0,0," + wb + "," + h + ",3," + data.length + ",");
            bo.write(data, 0, data.length);
        } else {
            byte[] data = packWhiteBits(bmp, THRESHOLD);
            ascii(bo, "BITMAP 0,0," + wb + "," + h + ",1,");
            bo.write(data, 0, data.length);
        }
        ascii(bo, "\r\n\r\n");
        return bo.toByteArray();
    }

    /** Page setup sent before each bitmap — the exact order used by com.itpp.e (task path). */
    static byte[] pageSetup(int widthMm, int heightMm, int paperType, int speed, int density) {
        StringBuilder sb = new StringBuilder();
        sb.append("SIZE ").append(widthMm).append(" mm,").append(heightMm).append(" mm\r\n");
        sb.append("DIRECTION 1,0\r\n");
        sb.append("SET CUTTER 1\r\n");
        sb.append(paperType == PAPER_CONTINUOUS ? "GAP 0 mm,0 mm"
                : paperType == PAPER_GAP ? "GAP 3.0 mm,0.0 mm" : "BLINE 3.0 mm,0.0 mm").append("\r\n");
        sb.append(paperType != PAPER_CONTINUOUS ? "SET GAP ON\r\n" : "SET GAP OFF\r\n");
        sb.append("SPEED ").append(snapSpeed(speed)).append("\r\n");
        sb.append("DENSITY ").append(Math.max(1, Math.min(15, density))).append("\r\n");
        sb.append("CLS\r\n");
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] printCommand() {
        return "PRINT 1,1\r\n".getBytes(StandardCharsets.US_ASCII);
    }

    private static float snapSpeed(float f) {
        if (f >= 6f) return 6f;
        float[] steps = {1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f, 5.5f, 6f};
        for (float s : steps) if (f <= s) return s;
        return 6f;
    }

    private static void ascii(ByteArrayOutputStream bo, String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        bo.write(b, 0, b.length);
    }
}
