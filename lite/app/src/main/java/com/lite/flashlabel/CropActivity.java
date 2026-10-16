package com.lite.flashlabel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

import static com.lite.flashlabel.Ui.MATCH;
import static com.lite.flashlabel.Ui.WRAP;
import static com.lite.flashlabel.Ui.dp;
import static com.lite.flashlabel.Ui.lp;
import static com.lite.flashlabel.Ui.margins;

/**
 * Sub-page for the four crop margins (mm). Shows the first page with the cut-off areas shaded
 * and returns the values to the print screen when the user goes back.
 */
public class CropActivity extends Activity {
    static final String EXTRA_VALUES = "crop";
    private static final String EXTRA_PAGE = "page", EXTRA_W = "w", EXTRA_H = "h", EXTRA_PX_PER_MM = "pxPerMm";
    private static final float MAX_MM = 300f;

    private final EditText[] fields = new EditText[4];
    private CropPreview preview;
    private int pageW, pageH;
    private float pxPerMm;

    /**
     * @param values  current margins {top, left, right, bottom} in mm
     * @param page    page image to preview, or null when no file is loaded
     * @param pxPerMm pixels per mm of that page (0 if none)
     */
    static Intent intent(Context c, float[] values, File page, int width, int height, float pxPerMm) {
        Intent i = new Intent(c, CropActivity.class).putExtra(EXTRA_VALUES, values);
        if (page != null) {
            i.putExtra(EXTRA_PAGE, page.getAbsolutePath()).putExtra(EXTRA_W, width).putExtra(EXTRA_H, height)
                    .putExtra(EXTRA_PX_PER_MM, pxPerMm);
        }
        return i;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float[] values = getIntent().getFloatArrayExtra(EXTRA_VALUES);
        if (values == null || values.length != 4) values = new float[4];
        String pagePath = getIntent().getStringExtra(EXTRA_PAGE);
        pageW = getIntent().getIntExtra(EXTRA_W, 0);
        pageH = getIntent().getIntExtra(EXTRA_H, 0);
        pxPerMm = getIntent().getFloatExtra(EXTRA_PX_PER_MM, 0f);

        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

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

        // Title bar
        FrameLayout bar = new FrameLayout(this);
        ImageView back = Ui.icon(this, Ui.Icon.BACK, Ui.TEXT, 22);
        back.setOnClickListener(v -> onBackPressed());
        bar.addView(back, new FrameLayout.LayoutParams(dp(this, 48), dp(this, 48), Gravity.START | Gravity.CENTER_VERTICAL));
        bar.addView(Ui.text(this, Strings.CROP_EDGES, 19, Ui.TEXT), new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER));
        TextView reset = Ui.text(this, Strings.RESET, 16, Ui.BLUE);
        reset.setPadding(dp(this, 12), dp(this, 8), dp(this, 16), dp(this, 8));
        reset.setOnClickListener(v -> {
            for (EditText f : fields) f.setText("0");
        });
        bar.addView(reset, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END | Gravity.CENTER_VERTICAL));
        root.addView(bar, lp(MATCH, dp(this, 56)));

        LinearLayout col = Ui.column(this);
        col.setPadding(dp(this, 16), dp(this, 4), dp(this, 16), dp(this, 16));

        // Preview of the first page with the cut-off areas shaded
        Bitmap bmp = pagePath == null ? null : Docs.thumb(new File(pagePath), 900);
        if (bmp != null) {
            preview = new CropPreview(this, bmp);
            preview.setBackgroundColor(Ui.PAGE);
            col.addView(preview, lp(MATCH, dp(this, 300)));
        } else {
            TextView none = Ui.text(this, Strings.CROP_NO_PREVIEW, 15, Ui.HINT);
            none.setGravity(Gravity.CENTER);
            none.setBackgroundColor(Ui.PAGE);
            col.addView(none, lp(MATCH, dp(this, 120)));
        }

        // Fields laid out like the page edges: top / left + right / bottom
        String[] names = {Strings.CROP_TOP, Strings.CROP_LEFT, Strings.CROP_RIGHT, Strings.CROP_BOTTOM};
        for (int i = 0; i < 4; i++) fields[i] = field(values[i]);
        col.addView(centered(labeled(names[Tspl.TOP], fields[Tspl.TOP])), margins(lp(MATCH, WRAP), this, 0, 16, 0, 0));
        LinearLayout middle = Ui.row(this);
        middle.addView(labeled(names[Tspl.LEFT], fields[Tspl.LEFT]), Ui.weight(WRAP, 1));
        middle.addView(new View(this), lp(dp(this, 24), 1));
        middle.addView(labeled(names[Tspl.RIGHT], fields[Tspl.RIGHT]), Ui.weight(WRAP, 1));
        col.addView(middle, margins(lp(MATCH, WRAP), this, 0, 12, 0, 0));
        col.addView(centered(labeled(names[Tspl.BOTTOM], fields[Tspl.BOTTOM])), margins(lp(MATCH, WRAP), this, 0, 12, 0, 0));

        TextView hint = Ui.text(this, Strings.CROP_HINT, 13, Ui.HINT);
        hint.setSingleLine(false);
        col.addView(hint, margins(lp(MATCH, WRAP), this, 0, 16, 0, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col, new FrameLayout.LayoutParams(MATCH, WRAP));
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        setContentView(root);
        updatePreview();
    }

    private EditText field(float value) {
        EditText e = new EditText(this);
        e.setTextSize(17);
        e.setTextColor(Ui.TEXT);
        e.setGravity(Gravity.CENTER);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        e.setBackground(Ui.round(this, Ui.CHIP, 5));
        e.setText(value == (int) value ? String.valueOf((int) value) : String.valueOf(value));
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updatePreview();
            }
        });
        return e;
    }

    private View labeled(String name, EditText field) {
        LinearLayout box = Ui.column(this);
        box.addView(Ui.text(this, name, 15, Ui.TEXT), lp(WRAP, WRAP));
        box.addView(field, margins(lp(MATCH, dp(this, 44)), this, 0, 6, 0, 0));
        return box;
    }

    private View centered(View child) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        row.addView(child, lp(dp(this, 150), WRAP));
        return row;
    }

    private float[] values() {
        float[] v = new float[4];
        for (int i = 0; i < 4; i++) {
            try {
                v[i] = Float.parseFloat(fields[i].getText().toString().trim());
            } catch (NumberFormatException e) {
                v[i] = 0f;
            }
            v[i] = Math.max(0f, Math.min(MAX_MM, v[i]));
        }
        return v;
    }

    private void updatePreview() {
        if (preview == null || fields[3] == null) return;
        float[] v = values();
        preview.setFractions(Tspl.cropFractions(v[Tspl.TOP], v[Tspl.LEFT], v[Tspl.RIGHT], v[Tspl.BOTTOM],
                pageW, pageH, pxPerMm));
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_VALUES, values()));
        super.onBackPressed();
    }

    /** Page image fitted to the view, with the removed margins shaded and the kept area outlined. */
    private static final class CropPreview extends View {
        private final Bitmap bitmap;
        private final Paint shade = new Paint(), border = new Paint(Paint.ANTI_ALIAS_FLAG),
                image = new Paint(Paint.FILTER_BITMAP_FLAG);
        private float[] f = new float[4];

        CropPreview(Context c, Bitmap bitmap) {
            super(c);
            this.bitmap = bitmap;
            shade.setColor(0xA0000000);
            border.setColor(Ui.RED);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(dp(c, 2));
        }

        void setFractions(float[] fractions) {
            f = fractions;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int pad = dp(getContext(), 8);
            float aw = getWidth() - 2f * pad, ah = getHeight() - 2f * pad;
            float s = Math.min(aw / bitmap.getWidth(), ah / bitmap.getHeight());
            float w = bitmap.getWidth() * s, h = bitmap.getHeight() * s;
            RectF page = new RectF((getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
            c.drawBitmap(bitmap, null, page, image);
            RectF keep = new RectF(page.left + w * f[Tspl.LEFT], page.top + h * f[Tspl.TOP],
                    page.right - w * f[Tspl.RIGHT], page.bottom - h * f[Tspl.BOTTOM]);
            c.drawRect(page.left, page.top, page.right, keep.top, shade);
            c.drawRect(page.left, keep.bottom, page.right, page.bottom, shade);
            c.drawRect(page.left, keep.top, keep.left, keep.bottom, shade);
            c.drawRect(keep.right, keep.top, page.right, keep.bottom, shade);
            c.drawRect(keep, border);
        }
    }
}
