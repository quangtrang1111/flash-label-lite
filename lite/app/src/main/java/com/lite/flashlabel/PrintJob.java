package com.lite.flashlabel;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Background print queue — same flow as com.itpp.e: poll status, send page, wait 1 s, next.
 * After the last page it keeps polling until the printer reports idle, so "finished" means
 * the labels have actually come out, not just that the bytes were sent.
 */
final class PrintJob extends Thread {
    interface Listener {
        void onProgress(String message);

        /**
         * @param printed true when every page was sent (and, normally, confirmed printed)
         * @param message error or warning to show, or null
         */
        void onFinished(boolean printed, String message);
    }

    private static final long FINISH_TIMEOUT_MS = 120_000;

    /** A page to print and the share of its height to cut off the bottom. */
    static final class Item {
        final File file;
        final float cropFraction;

        Item(File file, float cropFraction) {
            this.file = file;
            this.cropFraction = cropFraction;
        }
    }

    private final List<Item> pages;
    private final Prefs.Settings settings;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;

    PrintJob(List<Item> pages, Prefs.Settings settings, Listener listener) {
        super("print");
        this.pages = pages;
        this.settings = settings;
        this.listener = listener;
    }

    void cancel() {
        cancelled = true;
        interrupt();
    }

    @Override
    public void run() {
        Printer printer = Printer.get();
        int widthMm = (int) settings.widthMm, heightMm = (int) settings.heightMm;
        float angle = settings.direction == 0 ? 90f : 0f;
        boolean printed = false;
        String message = null;
        try {
            byte[] setup = Tspl.pageSetup(widthMm, heightMm, settings.paperType, settings.speed, settings.density);
            int index = 0;
            for (Item page : pages) {
                if (cancelled) break;
                index++;
                Bitmap full = BitmapFactory.decodeFile(page.file.getAbsolutePath());
                if (full == null) continue;
                Bitmap src = Tspl.cropBottom(full, page.cropFraction);
                Bitmap label = Tspl.renderLabel(src, widthMm, heightMm, angle, settings.dither);
                if (src != full) src.recycle();
                full.recycle();
                byte[] bitmap = Tspl.bitmapCommand(label, settings.compress);
                label.recycle();

                waitUntilReady(printer);
                if (cancelled) break;
                post(Strings.SENDING_PAGE + index + "/" + pages.size());
                printer.write(setup);
                printer.write(bitmap);
                printer.write(Tspl.printCommand());
                printer.write(new byte[bitmap.length]); // the SDK flushes with zeros of the same length
                SystemClock.sleep(1000);
            }
            if (!cancelled) {
                post(Strings.WAITING_FINISH);
                printed = true;
                if (!waitUntilFinished(printer)) message = Strings.FINISH_UNCONFIRMED;
            }
        } catch (IOException e) {
            message = Strings.CONNECTION_LOST;
        } catch (Throwable t) {
            message = t.toString();
        }
        final boolean ok = printed && !cancelled;
        final String msg = cancelled ? null : message;
        main.post(() -> listener.onFinished(ok, msg));
    }

    private void waitUntilReady(Printer printer) throws IOException {
        while (!cancelled) {
            if (!printer.isConnected()) throw new IOException("disconnected");
            int s = printer.status();
            if (s == Printer.READY) {
                post(Strings.PRINTER_READY);
                return;
            }
            post(statusText(s));
            SystemClock.sleep(500);
        }
    }

    /** Polls until the printer is idle again; false if it never confirmed within the timeout. */
    private boolean waitUntilFinished(Printer printer) throws IOException {
        long start = SystemClock.elapsedRealtime();
        while (!cancelled && SystemClock.elapsedRealtime() - start < FINISH_TIMEOUT_MS) {
            if (!printer.isConnected()) throw new IOException("disconnected");
            int s = printer.status();
            if (s == Printer.READY) return true;
            if (s != Printer.BUSY && s != Printer.NO_REPLY) post(statusText(s));
            SystemClock.sleep(500);
        }
        return false;
    }

    static String statusText(int s) {
        switch (s) {
            case Printer.BUSY:
            case Printer.NO_REPLY:
                return Strings.PRINTER_BUSY;
            case Printer.COVER_OPEN:
                return Strings.COVER_OPEN;
            case Printer.NO_PAPER:
                return Strings.NO_PAPER;
            case Printer.LOW_BATTERY:
                return Strings.LOW_BATTERY;
            default:
                return Strings.WAITING_REPLY;
        }
    }

    private void post(String msg) {
        main.post(() -> listener.onProgress(msg));
    }
}
