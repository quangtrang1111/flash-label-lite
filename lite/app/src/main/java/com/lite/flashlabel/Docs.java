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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    /** Renders one PDF/image into page PNGs named after {@code docId} so documents never collide. */
    static List<Rendered> load(Context c, Uri uri, int docId) throws IOException {
        File dir = pageDir(c);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        List<Rendered> pages = new ArrayList<>();
        ContentResolver cr = c.getContentResolver();
        String prefix = "d" + docId + "_p";
        String type = cr.getType(uri);
        boolean pdf = "application/pdf".equals(type) || uri.toString().toLowerCase().endsWith(".pdf");
        if (pdf) {
            File tmp = new File(dir, "src" + docId + ".pdf");
            copy(cr, uri, tmp);
            try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY);
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
            } finally {
                tmp.delete();
            }
        } else {
            Bitmap bmp = decodeImage(cr, uri);
            if (bmp != null) {
                int w = bmp.getWidth(), h = bmp.getHeight();
                pages.add(new Rendered(save(bmp, new File(dir, prefix + "0.png")), w, h, 0f));
            }
        }
        return pages;
    }

    private static Bitmap decodeImage(ContentResolver cr, Uri uri) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > MAX_SIDE) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap decoded;
        try (InputStream in = cr.openInputStream(uri)) {
            decoded = BitmapFactory.decodeStream(in, null, o);
        }
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
