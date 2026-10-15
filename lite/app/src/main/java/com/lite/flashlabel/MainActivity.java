package com.lite.flashlabel;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.lite.flashlabel.Ui.MATCH;
import static com.lite.flashlabel.Ui.WRAP;
import static com.lite.flashlabel.Ui.dp;
import static com.lite.flashlabel.Ui.lp;
import static com.lite.flashlabel.Ui.margins;

/**
 * Print preview screen (clone of FlashLabel's CommonPrintActivity), built without XML.
 * Every setting lives on this screen and is persisted when the user taps print.
 */
@SuppressWarnings("deprecation")
public class MainActivity extends Activity {
    private static final String TAG = "FlashLite";
    private static final int REQ_PICK = 1, REQ_PERMS = 2;

    /** Dropdown order for label types: black mark first (the default). */
    private static final int[] PAPER_ORDER = {Tspl.PAPER_BLACK_MARK, Tspl.PAPER_GAP, Tspl.PAPER_CONTINUOUS};
    private static final String[] PAPER_NAMES = {Strings.PAPER_BLACK_MARK, Strings.PAPER_GAP, Strings.PAPER_CONTINUOUS};
    private static final int DENSITY_MIN = 0, DENSITY_MAX = 15, SPEED_MIN = 2, SPEED_MAX = 8;

    private Prefs prefs;
    private Prefs.Settings settings;

    /** One printable page; {@code docId} groups the pages that came from the same file. */
    private static final class Page {
        final File file;
        final int docId;
        final Bitmap thumb;
        final int width, height;
        final float pxPerMm; // 0 for images
        boolean checked = true;

        Page(Docs.Rendered r, int docId, Bitmap thumb) {
            this.file = r.file;
            this.docId = docId;
            this.thumb = thumb;
            this.width = r.width;
            this.height = r.height;
            this.pxPerMm = r.pxPerMm;
        }
    }

    /**
     * Share of the page height removed by the "crop bottom" setting. PDFs use their real size;
     * images have none, so their width is taken to span the label width.
     */
    private float cropFraction(Page p) {
        if (settings.cropMm <= 0f || p.height <= 0) return 0f;
        float pxPerMm = p.pxPerMm > 0f ? p.pxPerMm : p.width / settings.widthMm;
        return Math.min(0.9f, settings.cropMm * pxPerMm / p.height);
    }

    private final List<Page> pages = new ArrayList<>();
    private int nextDocId;

    /** True once the current page list has been printed completely. */
    private boolean printed;
    private boolean connecting;

    private TextView chipHorizontal, verticalText, printerLabel, printButton;
    private FrameLayout chipVertical;
    private EditText etWidth, etHeight, etCrop;
    private LinearLayout strip;
    private PrintJob job;
    private ProgressDialog printDialog;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        settings = prefs.load();

        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(buildUi());

        showLabelSize(settings.widthMm, settings.heightMm);
        setDirection(settings.direction);
        updatePrintButton();
        if (!hasBtPermissions()) requestPermissions(btPermissions(), REQ_PERMS);
        Docs.clearCache(this);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPrinterLabel();
        autoConnect();
    }

    // ---------------------------------------------------------------- UI

    private View buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        root.addView(buildTitleBar(), lp(MATCH, dp(this, 56)));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.PAGE);
        scroll.addView(buildSettings(), new FrameLayout.LayoutParams(MATCH, WRAP));
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        root.addView(buildPreview(), lp(MATCH, WRAP));

        printButton = Ui.text(this, Strings.PRINT_NOW, 20, Color.WHITE);
        printButton.setGravity(Gravity.CENTER);
        printButton.setOnClickListener(v -> onPrintClicked());
        LinearLayout bottom = Ui.column(this);
        bottom.setBackgroundColor(Color.WHITE);
        bottom.addView(printButton, margins(lp(MATCH, dp(this, 50)), this, 28, 12, 28, 12));
        root.addView(bottom, lp(MATCH, WRAP));
        return root;
    }

    private View buildTitleBar() {
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(Color.WHITE);
        ImageView back = Ui.icon(this, Ui.Icon.BACK, Ui.TEXT, 22);
        back.setOnClickListener(v -> onBackPressed());
        bar.addView(back, new FrameLayout.LayoutParams(dp(this, 48), dp(this, 48), Gravity.START | Gravity.CENTER_VERTICAL));

        // The title is the Bluetooth printer control: shows the device/status, tap to pick or connect.
        printerLabel = Ui.text(this, "", 17, Ui.TEXT);
        printerLabel.setGravity(Gravity.CENTER);
        printerLabel.setCompoundDrawablePadding(dp(this, 8));
        printerLabel.setPadding(dp(this, 14), 0, dp(this, 12), 0);
        printerLabel.setMaxWidth(dp(this, 260));
        printerLabel.setOnClickListener(v -> choosePrinter(null));
        bar.addView(printerLabel, new FrameLayout.LayoutParams(WRAP, dp(this, 40), Gravity.CENTER));
        return bar;
    }

    private View buildSettings() {
        LinearLayout col = Ui.column(this);
        col.setBackgroundColor(Color.WHITE);
        col.setPadding(dp(this, 12), 0, dp(this, 12), dp(this, 4));

        // Label type: title and dropdown on one row
        LinearLayout typeRow = Ui.row(this);
        typeRow.addView(sectionTitle(Strings.LABEL_TYPE), Ui.weight(WRAP, 1));
        typeRow.addView(Ui.dropdown(this, PAPER_NAMES, indexOf(PAPER_ORDER, settings.paperType),
                i -> settings.paperType = PAPER_ORDER[i]), lp(dp(this, 170), dp(this, 44)));
        col.addView(typeRow, margins(lp(MATCH, WRAP), this, 2, 14, 0, 0));

        // Width / height
        LinearLayout labels = Ui.row(this);
        labels.addView(Ui.text(this, Strings.WIDTH_MM, 15.5f, Ui.TEXT), Ui.weight(WRAP, 1));
        labels.addView(Ui.text(this, Strings.HEIGHT_MM, 15.5f, Ui.TEXT), margins(Ui.weight(WRAP, 1), this, 12, 0, 0, 0));
        col.addView(labels, margins(lp(MATCH, WRAP), this, 2, 16, 0, 10));

        LinearLayout sizes = Ui.row(this);
        etWidth = sizeField();
        etHeight = sizeField();
        ImageView swap = Ui.icon(this, Ui.Icon.SWAP, Ui.HINT, 20);
        swap.setOnClickListener(v -> {
            readLabelSize();
            showLabelSize(settings.heightMm, settings.widthMm);
        });
        sizes.addView(etWidth, Ui.weight(dp(this, 44), 1));
        sizes.addView(swap, lp(dp(this, 40), dp(this, 44)));
        sizes.addView(etHeight, Ui.weight(dp(this, 44), 1));
        col.addView(sizes, lp(MATCH, WRAP));

        // Direction
        col.addView(sectionTitle(Strings.DIRECTION), margins(lp(MATCH, WRAP), this, 2, 16, 0, 10));
        LinearLayout dirs = Ui.row(this);
        chipHorizontal = Ui.chip(this, "ABC");
        chipHorizontal.setOnClickListener(v -> setDirection(1));
        chipVertical = new FrameLayout(this);
        verticalText = Ui.chip(this, "ABC");
        verticalText.setBackground(null);
        verticalText.setRotation(90f);
        chipVertical.addView(verticalText, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER));
        chipVertical.setOnClickListener(v -> setDirection(0));
        dirs.addView(chipHorizontal, margins(Ui.weight(dp(this, 44), 1), this, 0, 0, 12, 0));
        dirs.addView(chipVertical, margins(Ui.weight(dp(this, 44), 1), this, 12, 0, 0, 0));
        col.addView(dirs, margins(lp(MATCH, WRAP), this, 0, 0, 0, 8));

        // Former "Cài đặt khác" screen, now inline
        LinearLayout density = settingRow(Strings.DENSITY);
        density.addView(Ui.dropdown(this, numbers(DENSITY_MIN, DENSITY_MAX), clamp(settings.density, DENSITY_MIN, DENSITY_MAX) - DENSITY_MIN,
                i -> settings.density = DENSITY_MIN + i), lp(dp(this, 110), dp(this, 40)));
        col.addView(density, lp(MATCH, dp(this, 56)));

        LinearLayout speed = settingRow(Strings.SPEED);
        speed.addView(Ui.dropdown(this, numbers(SPEED_MIN, SPEED_MAX), clamp(settings.speed, SPEED_MIN, SPEED_MAX) - SPEED_MIN,
                i -> settings.speed = SPEED_MIN + i), lp(dp(this, 110), dp(this, 40)));
        col.addView(speed, lp(MATCH, dp(this, 56)));

        col.addView(switchRow(Strings.DITHER, settings.dither, true, on -> settings.dither = on), lp(MATCH, dp(this, 56)));
        col.addView(switchRow(Strings.COMPRESS, settings.compress, true, on -> settings.compress = on),
                lp(MATCH, dp(this, 56)));

        LinearLayout crop = settingRow(Strings.CROP_BOTTOM);
        etCrop = numberField();
        etCrop.setGravity(Gravity.CENTER);
        etCrop.setPadding(0, 0, 0, 0);
        etCrop.setText(fmt(settings.cropMm));
        etCrop.setOnFocusChangeListener((v, focus) -> {
            if (!focus) readCrop();
        });
        etCrop.setOnEditorActionListener((v, action, ev) -> {
            v.clearFocus();
            readCrop();
            return false;
        });
        crop.addView(etCrop, lp(dp(this, 110), dp(this, 40)));
        col.addView(crop, lp(MATCH, dp(this, 56)));
        return col;
    }

    private interface BoolCallback {
        void accept(boolean value);
    }

    private TextView sectionTitle(String s) {
        return Ui.text(this, s, 18, Ui.TEXT);
    }

    private LinearLayout settingRow(String title) {
        LinearLayout row = Ui.row(this);
        row.setPadding(dp(this, 2), 0, 0, 0);
        row.addView(Ui.text(this, title, 17, Ui.TEXT), Ui.weight(WRAP, 1));
        return row;
    }

    private View switchRow(String title, boolean on, boolean enabled, BoolCallback onChange) {
        LinearLayout row = settingRow(title);
        ((TextView) row.getChildAt(0)).setTextColor(enabled ? Ui.TEXT : Ui.HINT);
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setEnabled(enabled);
        sw.setOnCheckedChangeListener((b, checkedNow) -> onChange.accept(checkedNow));
        row.addView(sw, lp(WRAP, WRAP));
        return row;
    }

    /** Decimal number input on a rounded chip background. */
    private EditText numberField() {
        EditText e = new EditText(this);
        e.setTextSize(17);
        e.setTextColor(Ui.TEXT);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setBackground(Ui.round(this, Ui.CHIP, 5));
        e.setPadding(dp(this, 16), 0, dp(this, 16), 0);
        return e;
    }

    private EditText sizeField() {
        EditText e = numberField();
        e.setOnFocusChangeListener((v, focus) -> {
            if (!focus) readLabelSize();
        });
        e.setOnEditorActionListener((v, action, ev) -> {
            v.clearFocus();
            readLabelSize();
            return false;
        });
        return e;
    }

    private View buildPreview() {
        LinearLayout col = Ui.column(this);
        col.setBackgroundColor(Color.WHITE);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setBackgroundColor(Ui.PAGE);
        hs.setHorizontalScrollBarEnabled(false);
        strip = Ui.row(this);
        strip.setPadding(dp(this, 6), dp(this, 8), dp(this, 6), dp(this, 8));
        hs.addView(strip, new FrameLayout.LayoutParams(WRAP, dp(this, 175)));
        col.addView(hs, lp(MATCH, dp(this, 175)));
        return col;
    }

    private static String[] numbers(int min, int max) {
        String[] out = new String[max - min + 1];
        for (int i = 0; i < out.length; i++) out[i] = String.valueOf(min + i);
        return out;
    }

    private static int indexOf(int[] arr, int v) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
        return 0;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ---------------------------------------------------------------- state

    /** 1 = horizontal (angle 0), 0 = vertical (angle 90), same as setPrintDirection(). */
    private void setDirection(int d) {
        settings.direction = d;
        Ui.setChecked(chipHorizontal, d == 1);
        chipVertical.setBackground(Ui.round(this, d == 0 ? Ui.BLUE : Ui.CHIP, 5));
        verticalText.setTextColor(d == 0 ? Color.WHITE : Ui.TEXT);
        rebuildStrip();
    }

    private float angle() {
        return settings.direction == 0 ? 90f : 0f;
    }

    private static String fmt(float f) {
        return f == (int) f ? String.valueOf((int) f) : String.valueOf(f);
    }

    private void showLabelSize(float w, float h) {
        settings.widthMm = w;
        settings.heightMm = h;
        etWidth.setText(fmt(w));
        etHeight.setText(fmt(h));
        rebuildStrip();
    }

    /** Parses the size fields and clamps each side to 15..500 mm (refreshLabelSize()). */
    private void readLabelSize() {
        float w = clampSize(parse(etWidth.getText().toString()));
        float h = clampSize(parse(etHeight.getText().toString()));
        if (w != settings.widthMm || h != settings.heightMm
                || !fmt(w).equals(etWidth.getText().toString()) || !fmt(h).equals(etHeight.getText().toString())) {
            showLabelSize(w, h);
        }
    }

    /** Parses the crop field (0..300 mm) and refreshes the thumbnails when it changes. */
    private void readCrop() {
        float mm = Math.max(0f, Math.min(300f, parse(etCrop.getText().toString())));
        if (!fmt(mm).equals(etCrop.getText().toString())) etCrop.setText(fmt(mm));
        if (mm != settings.cropMm) {
            settings.cropMm = mm;
            rebuildStrip();
        }
    }

    private static float clampSize(float v) {
        return Math.max(15f, Math.min(500f, v));
    }

    private static float parse(String s) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private void updatePrintButton() {
        printButton.setText(printed ? Strings.PRINTED : Strings.PRINT_NOW);
        printButton.setBackground(Ui.round(this, printed ? Ui.RED : Ui.BUTTON, 8));
    }

    private void refreshPrinterLabel() {
        Printer p = Printer.get();
        boolean on = p.isConnected();
        printerLabel.setText(on ? p.name() : connecting ? Strings.CONNECTING : Strings.NOT_CONNECTED);
        printerLabel.setTextColor(on ? Ui.BLUE : Ui.TEXT);
        printerLabel.setBackground(Ui.round(this, on ? 0xFFE8F1FD : Ui.CHIP, 20));
        int color = on ? Ui.BLUE : Ui.HINT;
        Ui.Icon bt = new Ui.Icon(Ui.Icon.BLUETOOTH, color, dp(this, 2));
        bt.setBounds(0, 0, dp(this, 20), dp(this, 20));
        Ui.Icon arrow = new Ui.Icon(Ui.Icon.CHEVRON_DOWN, color, dp(this, 2));
        arrow.setBounds(0, 0, dp(this, 14), dp(this, 14));
        printerLabel.setCompoundDrawables(bt, null, arrow, null);
    }

    // ---------------------------------------------------------------- preview strip

    private void rebuildStrip() {
        if (strip == null) return;
        strip.removeAllViews();
        int viewH = dp(this, 145);
        int viewW = Math.max(1, (int) (viewH * (settings.widthMm / settings.heightMm)));
        int n = pages.size();
        for (int i = 0; i < n; i++) {
            final int index = i;
            final Page page = pages.get(i);
            FrameLayout card = new FrameLayout(this);
            card.setBackgroundColor(Color.WHITE);
            card.setPadding(dp(this, 6), dp(this, 6), dp(this, 6), dp(this, 6));

            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setBackgroundColor(Color.WHITE);
            if (page.thumb != null) {
                Bitmap shown = Tspl.cropBottom(page.thumb, cropFraction(page));
                iv.setImageBitmap(angle() == 0f ? shown : rotate(shown, angle()));
            }
            iv.setAlpha(page.checked ? 1f : 0.4f);
            iv.setOnClickListener(v -> openPreview(index));
            card.addView(iv, new FrameLayout.LayoutParams(viewW, viewH));

            // Round checkbox (top-left), same size as the remove button: include/exclude this page.
            ImageView check = new ImageView(this);
            int pad = dp(this, 9);
            check.setPadding(pad, pad, pad, pad);
            setCheckIcon(check, page.checked);
            check.setOnClickListener(v -> {
                page.checked = !page.checked;
                setCheckIcon(check, page.checked);
                iv.setAlpha(page.checked ? 1f : 0.4f);
            });
            card.addView(check, new FrameLayout.LayoutParams(dp(this, 40), dp(this, 40), Gravity.TOP | Gravity.START));

            // Small round x (top-right): removes the whole file this page belongs to.
            ImageView remove = new ImageView(this);
            int rp = dp(this, 9);
            remove.setPadding(rp, rp, rp, rp);
            remove.setImageDrawable(new Ui.Icon(Ui.Icon.REMOVE, 0xB3000000, dp(this, 1.6f)));
            remove.setOnClickListener(v -> removeDocument(page.docId));
            card.addView(remove, new FrameLayout.LayoutParams(dp(this, 40), dp(this, 40), Gravity.TOP | Gravity.END));

            TextView badge = Ui.text(this, (i + 1) + "/" + n, 13, Ui.TEXT);
            badge.setPadding(dp(this, 10), dp(this, 2), dp(this, 10), dp(this, 2));
            badge.setBackground(Ui.round(this, 0xDDEFEFEF, 12));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            bp.bottomMargin = dp(this, 6);
            card.addView(badge, bp);
            strip.addView(card, margins(lp(WRAP, WRAP), this, 6, 0, 6, 0));
        }

        LinearLayout add = Ui.column(this);
        add.setGravity(Gravity.CENTER);
        add.setBackground(Ui.round(this, Color.WHITE, 6));
        add.setPadding(dp(this, 6), 0, dp(this, 6), 0);
        add.addView(Ui.icon(this, Ui.Icon.ADD_FILE, Ui.BLUE, 30));
        TextView addText = Ui.text(this, n == 0 ? Strings.PICK_FILE : Strings.ADD_FILE, 13, Ui.BLUE);
        addText.setGravity(Gravity.CENTER);
        addText.setSingleLine(false);
        addText.setMaxLines(2);
        add.addView(addText, lp(MATCH, WRAP));
        add.setOnClickListener(v -> pickFile());
        strip.addView(add, margins(lp(dp(this, 120), dp(this, 157)), this, 6, 0, 6, 0));
    }

    private void setCheckIcon(ImageView view, boolean on) {
        view.setImageDrawable(new Ui.Icon(on ? Ui.Icon.CHECK_ON : Ui.Icon.CHECK_OFF, Ui.BLUE, dp(this, 2)));
    }

    private void openPreview(int index) {
        readCrop();
        List<File> files = new ArrayList<>();
        float[] crops = new float[pages.size()];
        for (int i = 0; i < pages.size(); i++) {
            files.add(pages.get(i).file);
            crops[i] = cropFraction(pages.get(i));
        }
        PreviewActivity.open(this, files, crops, index);
    }

    /** Drops every page of one file from the list and deletes its cached images. */
    /** Removes every page and deletes the cached images. */
    private void clearPages() {
        for (Page p : pages) {
            p.file.delete();
            if (p.thumb != null) p.thumb.recycle();
        }
        pages.clear();
    }

    private void removeDocument(int docId) {
        for (int i = pages.size() - 1; i >= 0; i--) {
            Page p = pages.get(i);
            if (p.docId != docId) continue;
            pages.remove(i);
            p.file.delete();
            if (p.thumb != null) p.thumb.recycle();
        }
        if (pages.isEmpty()) {
            printed = false;
            updatePrintButton();
        }
        rebuildStrip();
    }

    private static Bitmap rotate(Bitmap b, float angle) {
        Matrix m = new Matrix();
        m.postRotate(angle);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    // ---------------------------------------------------------------- files

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "image/*"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, REQ_PICK);
    }

    private void handleIntent(Intent intent) {
        List<Uri> uris = new ArrayList<>();
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) uris.addAll(list);
        } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            uris.add(intent.getData());
        }
        if (uris.isEmpty()) {
            rebuildStrip();
        } else {
            loadDocs(uris, true); // shared/opened from another app: show only these files
        }
    }

    /**
     * Loads files into the page list.
     * @param replace true to drop every current file first (only once the new ones loaded)
     */
    private void loadDocs(List<Uri> uris, boolean replace) {
        ProgressDialog pd = ProgressDialog.show(this, null, Strings.LOADING, true, false);
        int firstDocId = nextDocId;
        nextDocId += uris.size();
        new Thread(() -> {
            List<Page> loaded = new ArrayList<>();
            String error = null;
            for (int i = 0; i < uris.size(); i++) {
                try {
                    for (Docs.Rendered r : Docs.load(this, uris.get(i), firstDocId + i)) {
                        loaded.add(new Page(r, firstDocId + i, Docs.thumb(r.file, 600)));
                    }
                } catch (Exception e) {
                    Log.w(TAG, "load failed " + uris.get(i), e);
                    error = e.getMessage();
                }
            }
            final String err = error;
            runOnUiThread(() -> {
                if (!isFinishing()) pd.dismiss();
                if (err != null) Toast.makeText(this, Strings.OPEN_FAILED + err, Toast.LENGTH_LONG).show();
                if (loaded.isEmpty()) return;
                if (replace) clearPages();
                pages.addAll(loaded);
                printed = false; // list changed: back to "In ngay"
                updatePrintButton();
                rebuildStrip();
            });
        }, "load").start();
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_PICK && result == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) loadDocs(uris, false); // "Thêm tệp": append
        }
    }

    // ---------------------------------------------------------------- printer connection

    /** Silently reconnects to the last printer that connected successfully. */
    @SuppressWarnings("MissingPermission")
    private void autoConnect() {
        String mac = prefs.lastPrinter();
        if (mac == null || connecting || Printer.get().isConnected() || !hasBtPermissions()) return;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return;
        connecting = true;
        refreshPrinterLabel();
        new Thread(() -> {
            boolean ok = Printer.get().connect(mac, 10000);
            runOnUiThread(() -> {
                connecting = false;
                refreshPrinterLabel();
            });
        }, "auto-connect").start();
    }

    /** Reconnects to the last printer if known, otherwise shows the device list. */
    private void choosePrinter(Runnable then) {
        if (!hasBtPermissions()) {
            requestPermissions(btPermissions(), REQ_PERMS);
            return;
        }
        if (connecting) {
            toast(Strings.STILL_CONNECTING);
            return;
        }
        DevicePicker.Callback cb = ok -> {
            refreshPrinterLabel();
            if (ok && then != null) then.run();
        };
        String last = prefs.lastPrinter();
        if (then != null && last != null) {
            DevicePicker.connect(this, prefs, last, ok -> {
                if (ok) cb.onConnected(true);
                else DevicePicker.show(this, prefs, cb);
            });
        } else {
            DevicePicker.show(this, prefs, cb);
        }
    }

    // ---------------------------------------------------------------- printing

    private void onPrintClicked() {
        if (pages.isEmpty()) {
            toast(Strings.NO_FILE);
            return;
        }
        if (printed) {
            new AlertDialog.Builder(this)
                    .setTitle(Strings.REPRINT_TITLE)
                    .setMessage(Strings.REPRINT_MESSAGE)
                    .setPositiveButton(Strings.OK, (d, w) -> startPrintFlow())
                    .setNegativeButton(Strings.CANCEL, null)
                    .show();
        } else {
            startPrintFlow();
        }
    }

    /** Applies and saves every setting, makes sure a printer is connected, then prints. */
    private void startPrintFlow() {
        readLabelSize();
        readCrop();
        prefs.save(settings);
        if (connecting) {
            toast(Strings.STILL_CONNECTING);
        } else if (Printer.get().isConnected()) {
            checkStatusAndPrint();
        } else {
            choosePrinter(this::checkStatusAndPrint);
        }
    }

    private void checkStatusAndPrint() {
        List<PrintJob.Item> selected = new ArrayList<>();
        for (Page p : pages) if (p.checked) selected.add(new PrintJob.Item(p.file, cropFraction(p)));
        if (selected.isEmpty()) {
            toast(Strings.NO_PAGE_SELECTED);
            return;
        }
        new Thread(() -> {
            int status = Printer.get().status();
            runOnUiThread(() -> startPrint(selected, status));
        }, "status").start();
    }

    private void startPrint(List<PrintJob.Item> selected, int status) {
        refreshPrinterLabel();
        switch (status) {
            case Printer.BUSY:
                toast(Strings.PRINTING);
                return;
            case Printer.COVER_OPEN:
            case Printer.NO_PAPER:
                toast(Strings.COVER_OR_PAPER);
                return;
            case Printer.LOW_BATTERY:
                toast(Strings.LOW_BATTERY);
                return;
            case Printer.NO_REPLY:
                toast(Strings.DEVICE_ERROR);
                return;
        }
        printDialog = new ProgressDialog(this);
        printDialog.setMessage(Strings.PRINTING);
        printDialog.setCancelable(false);
        printDialog.setButton(ProgressDialog.BUTTON_NEGATIVE, Strings.CANCEL, (d, w) -> {
            if (job != null) job.cancel();
        });
        printDialog.show();
        job = new PrintJob(selected, settings, new PrintJob.Listener() {
            @Override
            public void onProgress(String message) {
                if (printDialog != null) printDialog.setMessage(message);
            }

            @Override
            public void onFinished(boolean ok, String message) {
                if (printDialog != null && printDialog.isShowing() && !isFinishing()) printDialog.dismiss();
                job = null;
                if (ok) {
                    printed = true;
                    updatePrintButton();
                }
                refreshPrinterLabel();
                if (message != null) toast(message);
            }
        });
        job.start();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- permissions

    private String[] btPermissions() {
        return Build.VERSION.SDK_INT >= 31
                ? new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    private boolean hasBtPermissions() {
        for (String p : btPermissions()) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        refreshPrinterLabel();
        autoConnect();
    }
}
