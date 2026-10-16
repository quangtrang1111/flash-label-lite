package com.lite.flashlabel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

import static com.lite.flashlabel.Ui.WRAP;
import static com.lite.flashlabel.Ui.dp;

/** Full-screen page viewer: opens at the tapped page, swipe for other pages, pinch/double-tap to zoom. */
public class PreviewActivity extends Activity {
    private static final String EXTRA_PAGES = "pages", EXTRA_CROPS = "crops", EXTRA_INDEX = "index";
    private static final int MAX_SIDE = 2400;

    private String[] pages;
    private float[] crops;
    private int index;
    private ZoomImageView image;
    private TextView counter;
    private Bitmap current;

    /** @param crops 4 values per page {top, left, right, bottom}, as for Tspl.crop (matches what will print) */
    static void open(Context c, List<File> files, float[] crops, int index) {
        String[] paths = new String[files.size()];
        for (int i = 0; i < paths.length; i++) paths[i] = files.get(i).getAbsolutePath();
        c.startActivity(new Intent(c, PreviewActivity.class).putExtra(EXTRA_PAGES, paths)
                .putExtra(EXTRA_CROPS, crops).putExtra(EXTRA_INDEX, index));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        pages = getIntent().getStringArrayExtra(EXTRA_PAGES);
        crops = getIntent().getFloatArrayExtra(EXTRA_CROPS);
        index = getIntent().getIntExtra(EXTRA_INDEX, 0);
        if (pages == null || pages.length == 0) {
            finish();
            return;
        }
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        image = new ZoomImageView(this);
        image.setSwipeListener(dir -> show(index + dir));
        root.addView(image, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        ImageView close = Ui.icon(this, Ui.Icon.CLOSE, Color.WHITE, 20);
        close.setOnClickListener(v -> finish());
        root.addView(close, new FrameLayout.LayoutParams(dp(this, 48), dp(this, 48), Gravity.TOP | Gravity.START));

        counter = Ui.text(this, "", 15, Color.WHITE);
        counter.setPadding(dp(this, 14), dp(this, 4), dp(this, 14), dp(this, 4));
        counter.setBackground(Ui.round(this, 0x80000000, 14));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cp.bottomMargin = dp(this, 24);
        root.addView(counter, cp);

        setContentView(root);
        show(index);
    }

    private void show(int i) {
        if (i < 0 || i >= pages.length) return;
        index = i;
        Bitmap full = Docs.thumb(new File(pages[i]), MAX_SIDE);
        if (full == null) return;
        Bitmap next = crops != null && crops.length >= (i + 1) * 4
                ? Tspl.crop(full, java.util.Arrays.copyOfRange(crops, i * 4, i * 4 + 4)) : full;
        if (next != full) full.recycle();
        image.setImageBitmap(next);
        if (current != null) current.recycle();
        current = next;
        counter.setText((i + 1) + "/" + pages.length);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (current != null) current.recycle();
    }
}
