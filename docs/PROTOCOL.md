# A318BT label printer protocol (reverse-engineered notes)

These notes describe how the official FlashLabel Android app (v2.4.7) talks to A318BT-family
Bluetooth label printers. They were written for interoperability, from an analysis of the app,
and are what FlashLabel Lite implements. No code or binaries from the original app are in this repository.

Key classes in the original app (obfuscated names as shown by jadx):

| Class | Role |
|---|---|
| `com/extralife/flashlabel/device/base/DeviceManager.java` | App-side glue: discovery, connect, choose print path |
| `com/extralife/flashlabel/print/LabelPrintActivity.java` | Renders the label → Bitmap, kicks off print |
| `com/itpp/a.java` | **Classic Bluetooth SPP socket** (connect / write / read) |
| `com/itpp/f.java` | Wrapper: chunked writes, ACL broadcast receiver |
| `com/itpp/b.java` | **Command builder** (TSPL text commands) |
| `com/itpp/c.java` | **Bitmap → 1-bit raster encoder** |
| `com/itpp/e.java` | Print-job queue (status poll → send → next) |
| `com/itpp/ap/*` | BLE GATT + Wi-Fi path, only for `RW401AP` printers (not needed for A318BT) |

The printer SDK is a third-party "ITPP" library (the vendor's contact info is embedded in `com/itpp/a.java`).

## 1. Discovery

- Plain `BluetoothAdapter.startDiscovery()`, 15 s timeout.
- Keep devices whose name **starts with** one of the supported prefixes (`A318BT`, `Y318BT`, `Q318BT`, …).
- Names **ending in `LE`** are dropped, because that's the printer's BLE twin and the app doesn't use it.

## 2. Connection: classic SPP, not BLE

```
device = adapter.getRemoteDevice(mac)
socket = device.createRfcommSocketToServiceRecord(UUID("00001101-0000-1000-8000-00805F9B34FB"))
socket.connect()        // retried every 200 ms for up to 20 s
out = socket.outputStream; in = socket.inputStream
```

- Writes are split into **3000-byte chunks** (`f.b(byte[])`).
- Disconnects are detected through the `ACL_DISCONNECTED` broadcast.
- On connect, DPI is set to **200** for A318BT. That's **8 dots/mm**: pixels = mm × 200 / 25.

## 3. Printer model family

`com.itpp.a.a(name)` maps the name prefix to a command dialect:

| Family | Models | Dialect |
|---|---|---|
| **1** | **A318BT**, Y318BT, Q318BT, Q318ABT, QR380A, QR-386A | TSPL, BITMAP mode 3 (zlib) or mode 1 (raw) |
| 2 | Y810BT, Y468, Y486BT, Y428BT, D3BT, … | TSPL, BITMAP mode 3 via Code941 |
| 3 | LTK-380, P80 | CPCL-like (`! 0 200 200 …`, `ZG`) |
| 4/5 | I-P-01 / A50, Y50P, YP11 | binary ESC (`1F 10 …`) |
| 0 | others | TSPL, BITMAP mode 3 via `Compress.codeTSPL` |

`DeviceManager.checkModel()` is true for family 1, so **A318BT uses the task-queue path** (`com/itpp/e.java`).

## 4. Status query (before every label)

Send `1B 68` (`ESC h`). The printer replies with 1 byte:

| Byte | Meaning |
|---|---|
| `0x00` | ready |
| bit0 `0x01` | printing / busy |
| bit1 `0x02` | cover open |
| bit2 `0x04` | out of paper |
| bit3 `0x08` | low battery |
| no reply | error (-5) |

The queue polls this every 500 ms until it reads `0x00`, then sends the page.

## 5. Page commands (exact sequence the app sends for A318BT)

All commands are ASCII/GB2312 text, each ending in `\r\n`:

```
SIZE 40 mm,30 mm
DIRECTION 1,0
SET CUTTER 1
GAP 3.0 mm,0.0 mm          ← paperType: 0 → "GAP 0 mm,0 mm" (continuous)
                                        1 → "GAP 3.0 mm,0.0 mm" (gap labels)
                                        2 → "BLINE 3.0 mm,0.0 mm" (black mark)
SET GAP ON                 ← OFF when paperType == 0
SPEED 3.0                  ← snapped to 1.0,1.5,…,6.0
DENSITY 8                  ← clamped 1..15 (app default 8)
CLS
BITMAP 0,0,<wBytes>,<h>,3,<len>,<len bytes of compressed raster>\r\n\r\n
PRINT 1,1
```

After that it writes **`len` zero bytes** (`portSendCmd(new byte[len])`). This looks like a buffer-flush workaround, so copy it.

Copies = repeat the whole block (one task per copy). There's a 1 s delay between tasks.

## 6. Bitmap encoding

1. The label is rendered to a Bitmap at `mm × 8` px. If the print mode is "photo", it's dithered with OpenCV Floyd–Steinberg.
2. Each row is packed MSB-first: `wBytes = ceil(width / 8)`, total size `wBytes × height`.
3. Threshold default is **175**.

There are two encodings:

| Mode | Encoder | Bit = 1 means | Payload |
|---|---|---|---|
| `,1,` (raw, `c.a(bmp,w,h,thr)`) | luma = .3R+.59G+.11B; bit=1 if pure white or luma ≥ thr | **white** (0 = burn) | raw bytes, no length field: `BITMAP x,y,wBytes,h,1,<data>` |
| `,3,` (compressed, `c.b(...)` + `Code941.code`) | avg(R,G,B) < thr → bit=1 | **black** before compression | `Code941.code()` output, length given as a field |

`libCode941.so` is zlib 1.2.3 plus a custom `_tspl_bitmap_zlib_table`. The exact framing (raw deflate vs zlib header, and whether bits get inverted) isn't confirmed yet.

## 7. How FlashLabel Lite uses this

- Transport, status polling, page setup and the per-page flow follow sections 2–5 exactly
  (see `lite/app/src/main/java/com/lite/flashlabel/Printer.java`, `Tspl.java` and `PrintJob.java`).
- Images are sent with **BITMAP mode 1 (raw)** by default, which needs no native code.
  A 40×30 mm label is about 40 B × 240 rows = 9.6 KB.
- Mode 3 needs the vendor's proprietary `libCode941.so`, which is **not** distributed here.
  If you have a copy, put it in `lite/app/src/main/jniLibs/arm64-v8a/` for a local build.
  The app detects it (JNI class `com.itpp.Code941`) and enables the "Compress image data" switch.
- OpenCV is replaced by a short Floyd–Steinberg ditherer, and PDFium by Android's `PdfRenderer`.

## 8. Verifying on hardware

To capture what any app actually sends to the printer:

1. On the phone, open Developer options, turn on **Enable Bluetooth HCI snoop log**, then turn Bluetooth off and on.
2. Print one label.
3. Run `adb bugreport bug.zip`, extract `FS/data/misc/bluetooth/logs/btsnoop_hci.log`, open it in Wireshark and filter on `btspp` / `rfcomm`.
