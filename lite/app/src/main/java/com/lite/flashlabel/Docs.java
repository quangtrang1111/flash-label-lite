package com.lite.flashlabel;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Turns PDFs/images into page PNGs in the cache dir (replaces PDFium + Glide). */
final class Docs {
    private static final int MAX_SIDE = 2400;

    private Docs() {
    }

    private static File pageDir(Context c) {
        return new File(c.getCacheDir(), "pages");
    }

    /** Deletes page images left over from a previous session. */
    static void clearCache(Context c) {
        File[] old = pageDir(c).listFiles();
        if (old != null) for (File f : old) f.delete();
    }

    /** A rendered page image plus the size info needed to crop it in millimetres. */
    static final class Rendered {
        final File file;
        final int width, height;
        /** Pixels per millimetre of the original page, or 0 for images (no physical size). */
        final float pxPerMm;

        Rendered(File file, int width, int height, float pxPerMm) {
            this.file = file;
            this.width = width;
            this.height = height;
            this.pxPerMm = pxPerMm;
        }
    }

    /** Downloads are capped so a wrong link can't fill the cache. */
    private static final long MAX_DOWNLOAD_BYTES = 50L * 1024 * 1024;

    static boolean isRemote(Uri uri) {
        String s = uri.getScheme();
        return "http".equalsIgnoreCase(s) || "https".equalsIgnoreCase(s);
    }

    /**
     * Renders one PDF/image into page PNGs named after {@code docId} so documents never collide.
     * {@code uri} may be a content/file URI or an http(s) link, which is downloaded first.
     */
    static List<Rendered> load(Context c, Uri uri, int docId) throws IOException {
        File dir = pageDir(c);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        List<Rendered> pages = new ArrayList<>();
        String prefix = "d" + docId + "_p";
        File src = new File(dir, "src" + docId);
        try {
            String type;
            if (isRemote(uri)) {
                type = download(uri, src);
            } else {
                ContentResolver cr = c.getContentResolver();
                copy(cr, uri, src);
                type = cr.getType(uri);
            }
            if (isPdf(src, type)) {
                try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY);
                     PdfRenderer r = new PdfRenderer(fd)) {
                    for (int i = 0; i < r.getPageCount(); i++) {
                        try (PdfRenderer.Page page = r.openPage(i)) {
                            // ~300 dpi, capped so huge pages don't blow memory
                            float scale = Math.min(300f / 72f, MAX_SIDE / (float) Math.max(page.getWidth(), page.getHeight()));
                            Bitmap bmp = Bitmap.createBitmap(Math.max(1, (int) (page.getWidth() * scale)),
                                    Math.max(1, (int) (page.getHeight() * scale)), Bitmap.Config.ARGB_8888);
                            new Canvas(bmp).drawColor(Color.WHITE);
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                            int w = bmp.getWidth(), h = bmp.getHeight();
                            // PDF units are points (1/72 inch)
                            pages.add(new Rendered(save(bmp, new File(dir, prefix + i + ".png")), w, h, scale * 72f / 25.4f));
                        }
                    }
                }
            } else {
                Bitmap bmp = decodeImage(src);
                if (bmp == null) throw new IOException("not a PDF or image");
                int w = bmp.getWidth(), h = bmp.getHeight();
                pages.add(new Rendered(save(bmp, new File(dir, prefix + "0.png")), w, h, 0f));
            }
        } finally {
            src.delete();
        }
        return pages;
    }

    /** PDF if the MIME type says so or the file starts with "%PDF" (links often have no extension). */
    private static boolean isPdf(File f, String mime) throws IOException {
        if (mime != null && mime.toLowerCase().startsWith("application/pdf")) return true;
        byte[] head = new byte[4];
        try (InputStream in = new FileInputStream(f)) {
            if (in.read(head) < 4) return false;
        }
        return head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
    }

    /** Downloads an http(s) link to {@code dst}; returns the server's Content-Type (may be null). */
    private static String download(Uri uri, File dst) throws IOException {
        URL url = new URL(uri.toString());
        for (int redirects = 0; redirects < 5; redirects++) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(false); // handled below so http->https redirects work too
            try {
                int code = conn.getResponseCode();
                if (code >= 300 && code < 400 && conn.getHeaderField("Location") != null) {
                    url = new URL(url, conn.getHeaderField("Location"));
                    continue;
                }
                if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
                if (conn.getContentLengthLong() > MAX_DOWNLOAD_BYTES) throw new IOException("file is larger than 50 MB");
                long total = 0;
                try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(dst)) {
                    byte[] buf = new byte[64 * 1024];
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        total += r;
                        if (total > MAX_DOWNLOAD_BYTES) throw new IOException("file is larger than 50 MB");
                        out.write(buf, 0, r);
                    }
                }
                return conn.getContentType();
            } finally {
                conn.disconnect();
            }
        }
        throw new IOException("too many redirects");
    }

    private static Bitmap decodeImage(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > MAX_SIDE) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap decoded = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (decoded == null) return null;
        // flatten transparency onto white
        Bitmap out = Bitmap.createBitmap(decoded.getWidth(), decoded.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        cv.drawColor(Color.WHITE);
        cv.drawBitmap(decoded, 0, 0, null);
        decoded.recycle();
        return out;
    }

    private static File save(Bitmap bmp, File f) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bmp.recycle();
        return f;
    }

    private static void copy(ContentResolver cr, Uri uri, File dst) throws IOException {
        try (InputStream in = cr.openInputStream(uri); OutputStream out = new FileOutputStream(dst)) {
            if (in == null) throw new IOException("cannot open " + uri);
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        }
    }

    /** Small preview bitmap for the thumbnail strip. */
    static Bitmap thumb(File f, int maxSide) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }
}
