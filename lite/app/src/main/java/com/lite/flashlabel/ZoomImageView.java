package com.lite.flashlabel;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/**
 * ImageView with pinch-zoom, pan and double-tap zoom. When not zoomed in,
 * a horizontal fling is reported as a page swipe.
 */
final class ZoomImageView extends ImageView {
    interface SwipeListener {
        /** @param direction +1 = next page, -1 = previous page */
        void onSwipe(int direction);
    }

    private static final float MAX_ZOOM = 5f, DOUBLE_TAP_ZOOM = 2.5f;

    private final Matrix base = new Matrix();   // fit-center
    private final Matrix user = new Matrix();   // zoom/pan on top
    private final Matrix draw = new Matrix();
    private final ScaleGestureDetector scaler;
    private final GestureDetector gestures;
    private SwipeListener swipeListener;

    ZoomImageView(Context c) {
        super(c);
        setScaleType(ScaleType.MATRIX);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float target = Math.max(1f, Math.min(MAX_ZOOM, zoom() * d.getScaleFactor()));
                float f = target / zoom();
                user.postScale(f, f, d.getFocusX(), d.getFocusY());
                apply();
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (zoom() > 1.01f) {
                    user.postTranslate(-dx, -dy);
                    apply();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (zoom() > 1.01f) {
                    user.reset();
                } else {
                    user.postScale(DOUBLE_TAP_ZOOM, DOUBLE_TAP_ZOOM, e.getX(), e.getY());
                }
                apply();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (zoom() <= 1.01f && swipeListener != null && Math.abs(vx) > Math.abs(vy) && Math.abs(vx) > 600) {
                    swipeListener.onSwipe(vx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }
        });
    }

    void setSwipeListener(SwipeListener l) {
        swipeListener = l;
    }

    @Override
    public void setImageDrawable(Drawable d) {
        super.setImageDrawable(d);
        user.reset();
        fit();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fit();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        if (!scaler.isInProgress()) gestures.onTouchEvent(e);
        return true;
    }

    private float zoom() {
        float[] v = new float[9];
        user.getValues(v);
        return v[Matrix.MSCALE_X];
    }

    private void fit() {
        Drawable d = getDrawable();
        if (d == null || getWidth() == 0) return;
        base.setRectToRect(new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight()),
                new RectF(0, 0, getWidth(), getHeight()), Matrix.ScaleToFit.CENTER);
        apply();
    }

    /** Keeps the zoomed image from being dragged past its edges. */
    private void apply() {
        Drawable d = getDrawable();
        if (d == null) return;
        draw.set(base);
        draw.postConcat(user);
        RectF r = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        draw.mapRect(r);
        float dx = 0, dy = 0;
        if (r.width() <= getWidth()) dx = (getWidth() - r.width()) / 2 - r.left;
        else if (r.left > 0) dx = -r.left;
        else if (r.right < getWidth()) dx = getWidth() - r.right;
        if (r.height() <= getHeight()) dy = (getHeight() - r.height()) / 2 - r.top;
        else if (r.top > 0) dy = -r.top;
        else if (r.bottom < getHeight()) dy = getHeight() - r.bottom;
        user.postTranslate(dx, dy);
        draw.set(base);
        draw.postConcat(user);
        setImageMatrix(draw);
    }
}
