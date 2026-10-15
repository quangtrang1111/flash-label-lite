package com.lite.flashlabel;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Classic Bluetooth SPP connection to the label printer.
 * Mirrors com.itpp.a / com.itpp.f from the original SDK.
 */
public final class Printer {
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int CHUNK = 3000;

    /** Name prefixes the original app accepts (com.itpp.a#a()). */
    static final String[] PREFIXES = {"ITPP941B", "J500", "F500BT", "Y810BT", "Y480BT", "Y458BT", "Y528BT",
            "Y468", "RH-40BT", "Y428BT", "Y418BT", "Y888BT", "Y486BT", "Y318BT", "A30BT", "A31BT", "W300BT",
            "D3BT", "Q318BT", "Q318ABT", "Y338", "AY380", "M30", "T6310", "X4BT", "ITPP130B", "Y486ZBT",
            "A318BT", "QR380A", "QR-386A", "A428", "Y468BT", "P80", "LTK-380", "A50", "Y50P", "YP11", "I-P-01",
            "Y486AP", "Y811BT", "Y812BT", "PR40", "Y813BT", "DMD133"};

    // Status codes, same values as ITPPCommand.ITPP_PrinterState().
    static final int READY = 0, BUSY = -1, COVER_OPEN = -2, NO_PAPER = -3, LOW_BATTERY = -4, NO_REPLY = -5;

    private static final Printer INSTANCE = new Printer();

    private BluetoothSocket socket;
    private OutputStream out;
    private InputStream in;
    private String name = "";
    private String address = "";

    private Printer() {
    }

    static Printer get() {
        return INSTANCE;
    }

    static boolean isSupportedName(String n) {
        if (n == null || n.isEmpty() || n.endsWith("LE")) return false;
        for (String p : PREFIXES) if (n.startsWith(p)) return true;
        return false;
    }

    /** Family 1 printers (A318BT and friends) use the task path with BITMAP mode 3. */
    static boolean isFamily1(String n) {
        return n.startsWith("A318BT") || n.startsWith("Y318BT") || n.startsWith("Q318BT") || n.startsWith("Q318ABT")
                || n.startsWith("QR380A") || n.startsWith("QR-380A") || n.startsWith("QR-386A");
    }

    synchronized boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    synchronized String name() {
        return name;
    }

    synchronized String address() {
        return address;
    }

    /** Blocking connect; retries every 200 ms like the SDK. */
    @SuppressWarnings("MissingPermission")
    synchronized boolean connect(String mac, int timeoutMs) {
        disconnect();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return false;
        adapter.cancelDiscovery();
        BluetoothDevice device = adapter.getRemoteDevice(mac);
        long start = SystemClock.elapsedRealtime();
        while (true) {
            BluetoothSocket s = null;
            try {
                s = device.createRfcommSocketToServiceRecord(SPP);
                s.connect();
                socket = s;
                out = s.getOutputStream();
                in = s.getInputStream();
                name = device.getName() == null ? mac : device.getName();
                address = mac;
                return true;
            } catch (IOException | SecurityException e) {
                closeQuietly(s);
                if (SystemClock.elapsedRealtime() - start > timeoutMs) return false;
                SystemClock.sleep(200);
            }
        }
    }

    synchronized void disconnect() {
        closeQuietly(socket);
        socket = null;
        out = null;
        in = null;
    }

    private static void closeQuietly(BluetoothSocket s) {
        if (s == null) return;
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }

    synchronized void write(byte[] data) throws IOException {
        if (out == null) throw new IOException("not connected");
        try {
            for (int off = 0; off < data.length; off += CHUNK) {
                out.write(data, off, Math.min(CHUNK, data.length - off));
            }
            out.flush();
        } catch (IOException e) {
            disconnect();
            throw e;
        }
    }

    /** Returns the first chunk that arrives within the timeout (clamped to 200..5000 ms), or null. */
    synchronized byte[] read(int timeoutMs) {
        if (in == null) return null;
        timeoutMs = Math.max(200, Math.min(5000, timeoutMs));
        long start = SystemClock.elapsedRealtime();
        try {
            do {
                int n = in.available();
                if (n > 0) {
                    byte[] buf = new byte[n];
                    int r = in.read(buf);
                    if (r <= 0) return null;
                    if (r == n) return buf;
                    byte[] cut = new byte[r];
                    System.arraycopy(buf, 0, cut, 0, r);
                    return cut;
                }
                SystemClock.sleep(20);
            } while (SystemClock.elapsedRealtime() - start <= timeoutMs);
        } catch (IOException e) {
            disconnect();
        }
        return null;
    }

    /** ESC h status query, decoded exactly like the SDK. */
    int status() {
        read(10); // drain stale bytes
        try {
            write(new byte[]{0x1B, 0x68});
        } catch (IOException e) {
            return NO_REPLY;
        }
        byte[] r = read(2000);
        if (r != null && r.length > 0 && r.length <= 2) {
            int b = r[0];
            if (b == 0) return READY;
            if ((b & 2) == 2) return COVER_OPEN;
            if ((b & 4) == 4) return NO_PAPER;
            if ((b & 8) == 8) return LOW_BATTERY;
            if ((b & 1) == 1) return BUSY;
        }
        return NO_REPLY;
    }
}
