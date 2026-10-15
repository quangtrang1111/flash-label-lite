package com.lite.flashlabel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Programmatic-UI helpers so the app needs no layout/drawable XML. */
final class Ui {
    static final int BLUE = 0xFF0075E3;     // shape_solid_ff0075e3
    static final int CHIP = 0xFFF4F5F7;     // shape_solid_fff4f5f7
    static final int PAGE = 0xFFF2F3F5;
    static final int TEXT = 0xFF222222;
    static final int HINT = 0xFF999999;
    static final int RED = 0xFFE60012;
    static final int BUTTON = 0xFF3476FE;
    static final int DIVIDER = 0xFFEBEBEB;

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** Rounded toggle chip used for paper type / direction. */
    static TextView chip(Context c, String s) {
        TextView t = text(c, s, 16, TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 6), 0, dp(c, 6), 0);
        return t;
    }

    static void setChecked(TextView chip, boolean on) {
        chip.setBackground(round(chip.getContext(), on ? BLUE : CHIP, 5));
        chip.setTextColor(on ? 0xFFFFFFFF : TEXT);
    }

    /**
     * Compact dropdown: a Spinner on a rounded chip background with a down arrow.
     * Use {@link #spinnerOf(View)} to reach the Spinner inside.
     */
    static FrameLayout dropdown(Context c, String[] items, int selected, IntCallback onSelect) {
        Spinner spinner = new Spinner(c, Spinner.MODE_DROPDOWN);
        spinner.setBackground(null);
        spinner.setPadding(0, 0, 0, 0);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(c, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                t.setTextColor(TEXT);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setPadding(dp(c, 14), 0, dp(c, 34), 0);
                return t;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getDropDownView(position, convertView, parent);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                t.setTextColor(TEXT);
                t.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
                return t;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onSelect.accept(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        FrameLayout box = new FrameLayout(c);
        box.setBackground(round(c, CHIP, 5));
        box.addView(spinner, new FrameLayout.LayoutParams(MATCH, MATCH));
        ImageView arrow = new ImageView(c);
        arrow.setImageDrawable(new Icon(Icon.CHEVRON_DOWN, HINT, dp(c, 2)));
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(dp(c, 16), dp(c, 16), Gravity.END | Gravity.CENTER_VERTICAL);
        ap.rightMargin = dp(c, 12);
        box.addView(arrow, ap);
        box.setOnClickListener(v -> spinner.performClick());
        return box;
    }

    static Spinner spinnerOf(View dropdown) {
        return (Spinner) ((FrameLayout) dropdown).getChildAt(0);
    }

    interface IntCallback {
        void accept(int value);
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams weight(int h, float weight) {
        return new LinearLayout.LayoutParams(0, h, weight);
    }

    static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, Context c, int l, int t, int r, int b) {
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(DIVIDER);
        return v;
    }

    static ImageView icon(Context c, int type, int color, int sizeDp) {
        return icon(c, type, color, sizeDp, 48);
    }

    /** Icon of {@code sizeDp} centered in a square touch box of {@code boxDp}. */
    static ImageView icon(Context c, int type, int color, int sizeDp, int boxDp) {
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(new Icon(type, color, dp(c, 2)));
        int p = dp(c, (boxDp - sizeDp) / 2f);
        iv.setPadding(p, p, p, p);
        iv.setLayoutParams(lp(dp(c, boxDp), dp(c, boxDp)));
        return iv;
    }

    /** Tiny stroke icons drawn with Path — replaces the original's PNG/vector drawables. */
    static final class Icon extends Drawable {
        static final int BACK = 0, CHEVRON = 1, MINUS = 2, PLUS = 3, SWAP = 4, RADIO_OFF = 5, RADIO_ON = 6,
                CLOSE = 7, BLUETOOTH = 8, ADD_FILE = 9, CHEVRON_DOWN = 10,
                CHECK_ON = 11, CHECK_OFF = 12, REMOVE = 13;
        private final int type;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Icon(int type, int color, float stroke) {
            this.type = type;
            paint.setColor(color);
            paint.setStrokeWidth(stroke);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height();
            c.save();
            c.translate(getBounds().left, getBounds().top);
            Path p = new Path();
            switch (type) {
                case BACK:
                    p.moveTo(w * .65f, h * .15f); p.lineTo(w * .3f, h * .5f); p.lineTo(w * .65f, h * .85f);
                    break;
                case CHEVRON:
                    p.moveTo(w * .35f, h * .15f); p.lineTo(w * .7f, h * .5f); p.lineTo(w * .35f, h * .85f);
                    break;
                case CHECK_ON:
                case CHECK_OFF:
                case REMOVE: {
                    // Filled circle (paint color) with a white mark; CHECK_OFF is a white disc with a gray ring.
                    float r = Math.min(w, h) / 2f;
                    Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                    fill.setStyle(Paint.Style.FILL);
                    fill.setColor(type == CHECK_OFF ? 0xFFFFFFFF : paint.getColor());
                    c.drawCircle(w / 2, h / 2, r, fill);
                    Paint mark = new Paint(paint);
                    if (type == CHECK_OFF) {
                        mark.setColor(0xFFB0B4BA);
                        c.drawCircle(w / 2, h / 2, r - mark.getStrokeWidth() / 2, mark);
                        break;
                    }
                    mark.setColor(0xFFFFFFFF);
                    if (type == CHECK_ON) {
                        p.moveTo(w * .28f, h * .52f); p.lineTo(w * .44f, h * .68f); p.lineTo(w * .73f, h * .36f);
                    } else {
                        p.moveTo(w * .34f, h * .34f); p.lineTo(w * .66f, h * .66f);
                        p.moveTo(w * .66f, h * .34f); p.lineTo(w * .34f, h * .66f);
                    }
                    c.drawPath(p, mark);
                    c.restore();
                    return;
                }
                case CHEVRON_DOWN:
                    p.moveTo(w * .2f, h * .35f); p.lineTo(w * .5f, h * .65f); p.lineTo(w * .8f, h * .35f);
                    break;
                case PLUS:
                    p.moveTo(w * .5f, h * .1f); p.lineTo(w * .5f, h * .9f);
                    // fall through to the horizontal bar
                case MINUS:
                    p.moveTo(w * .1f, h * .5f); p.lineTo(w * .9f, h * .5f);
                    break;
                case SWAP:
                    p.moveTo(w * .1f, h * .38f); p.lineTo(w * .9f, h * .38f); p.lineTo(w * .7f, h * .2f);
                    p.moveTo(w * .9f, h * .62f); p.lineTo(w * .1f, h * .62f); p.lineTo(w * .3f, h * .8f);
                    break;
                case RADIO_OFF:
                case RADIO_ON:
                    c.drawCircle(w / 2, h / 2, Math.min(w, h) / 2 - paint.getStrokeWidth(), paint);
                    if (type == RADIO_ON) {
                        Paint f = new Paint(paint);
                        f.setStyle(Paint.Style.FILL);
                        c.drawCircle(w / 2, h / 2, Math.min(w, h) / 4.5f, f);
                    }
                    break;
                case CLOSE:
                    p.moveTo(w * .2f, h * .2f); p.lineTo(w * .8f, h * .8f);
                    p.moveTo(w * .8f, h * .2f); p.lineTo(w * .2f, h * .8f);
                    break;
                case BLUETOOTH:
                    p.moveTo(w * .28f, h * .3f); p.lineTo(w * .72f, h * .7f); p.lineTo(w * .5f, h * .9f);
                    p.lineTo(w * .5f, h * .1f); p.lineTo(w * .72f, h * .3f); p.lineTo(w * .28f, h * .7f);
                    break;
                case ADD_FILE:
                    p.addRoundRect(w * .2f, h * .1f, w * .8f, h * .9f, w * .06f, w * .06f, Path.Direction.CW);
                    p.moveTo(w * .5f, h * .35f); p.lineTo(w * .5f, h * .65f);
                    p.moveTo(w * .35f, h * .5f); p.lineTo(w * .65f, h * .5f);
                    break;
            }
            c.drawPath(p, paint);
            c.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
