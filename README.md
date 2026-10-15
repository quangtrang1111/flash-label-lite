<p align="center"><img src="docs/icon.png" width="96" alt="FlashLabel Lite icon"></p>

<h1 align="center">FlashLabel Lite</h1>

<p align="center">
A tiny Android app (~1.3 MB) for printing PDF shipping labels and images on
A318BT-family Bluetooth thermal label printers.
</p>

<p align="center">
<a href="https://ko-fi.com/quangtrang1111/?hidefeed=true&widget=true&embed=true"><img src="https://img.shields.io/badge/Ko--fi-Buy%20me%20a%20coffee-FF5E5B?logo=ko-fi&logoColor=white" alt="Support me on Ko-fi"></a>
</p>

<p align="center">
<a href="https://github.com/quangtrang1111/flash-label-lite/releases/latest">Download the latest APK</a> ·
<a href="docs/PROTOCOL.md">Printer protocol notes</a>
</p>

---

The official FlashLabel app is about 48 MB because it bundles OpenCV, PDFium and a full label
editor. FlashLabel Lite does one job — print a PDF or image on a label printer — in about
2,000 lines of plain Java with **no dependencies** and **no XML layouts**.

## Features

- **Share to print**: share a PDF or image from Shopee, your file manager or any app, or pick files in the app.
- **Multiple files**: add several files, include or exclude pages with a tap, remove a whole file with ×.
- **Page preview**: full-screen viewer with swipe, pinch-zoom and double-tap zoom.
- **Label settings on one screen**: label type (black mark / gap / continuous), size in mm, print direction,
  density, speed, photo dithering and **crop page bottom (mm)**. All settings are saved when you print.
- **Remembers your printer** and reconnects automatically when the app opens.
- **Knows when printing is done**: waits for the printer to finish, then marks the job "Printed"
  and asks before printing the same pages again.
- **English and Vietnamese**, chosen from the device language.

## Supported printers

The app talks to printers whose Bluetooth name starts with one of the prefixes used by the
official app, including `A318BT`, `Y318BT`, `Q318BT`, `Q318ABT`, `QR380A` and `QR-386A`
(see `Printer.PREFIXES`). It has been developed against an **A318BT**. Other models from the same
family may use different command dialects; reports are welcome.

## Install

1. Download the APK from [Releases](https://github.com/quangtrang1111/flash-label-lite/releases/latest).
2. Allow installing from unknown sources when Android asks.
3. Pair the printer in Android Bluetooth settings (or let the app find it), then open the app.

Requires Android 7.0 (API 24) or newer and Bluetooth permission.

## Build

Requirements: JDK 17+ and the Android SDK (API 35).

```sh
cd lite
./gradlew assembleRelease
# -> lite/app/build/outputs/apk/release/app-release.apk
```

Without signing variables the release build is signed with your local debug key.

### Optional: image compression

The printer also accepts zlib-compressed bitmaps, but producing them needs the vendor's proprietary
`libCode941.so`, which is **not included** in this repository. Without it the app sends raw bitmaps,
which works fine and is only slightly slower. If you have a copy, put it in
`lite/app/src/main/jniLibs/arm64-v8a/` for a local build to enable the "Compress image data" switch.

## Releases (CI)

Every push to `main` that changes `lite/` builds the APK with GitHub Actions and publishes it as a
GitHub release (`v1.0.<run number>`). See [`.github/workflows/release.yml`](.github/workflows/release.yml).

To sign releases with a stable key (so new versions install over old ones), add these repository
secrets: `KEYSTORE_BASE64` (base64 of your `.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Without them each release is signed with a throwaway debug key.

## Project layout

```
lite/app/src/main/java/com/lite/flashlabel/
  MainActivity.java     print screen (all UI built in code)
  PreviewActivity.java  full-screen page viewer
  Printer.java          Bluetooth SPP connection + status query
  Tspl.java             label rendering, dithering, TSPL commands
  PrintJob.java         background print queue
  Docs.java             PDF/image loading (PdfRenderer)
  Strings.java          English / Vietnamese text
docs/PROTOCOL.md        how the printer protocol works
```

## License

FlashLabel Lite is licensed under the [GNU GPL v3.0](LICENSE) with an **additional attribution term**
(GPL-3.0 section 7(b)). Any app or product that contains or is derived from this code must show, in a
place users can see (for example the About screen, settings screen or app store description):

> Based on FlashLabel Lite by Quang Trang - https://github.com/quangtrang1111/flash-label-lite

See [NOTICE](NOTICE) for details.

## Disclaimer

This is an independent project. It is not affiliated with or endorsed by the makers of FlashLabel
or of the printers. "FlashLabel", "A318BT" and other product names belong to their respective owners.
The protocol was documented for interoperability; no code or binaries from the official app are included.
