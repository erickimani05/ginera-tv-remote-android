package com.ginera.tvremote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

public class TouchpadView extends View {
    public interface Listener {
        void onMove(int dx, int dy);
        void onClick();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Listener listener;
    private float lastX;
    private float lastY;
    private float downX;
    private float downY;

    public TouchpadView(Context context) {
        super(context);
        paint.setColor(Color.rgb(31, 41, 55));
        setMinimumHeight(dp(170));
        setContentDescription("LG touchpad");
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(18), dp(18), paint);
        paint.setColor(Color.LTGRAY);
        paint.setTextSize(dp(16));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Swipe to move • Tap to select", getWidth() / 2f, getHeight() / 2f, paint);
        paint.setColor(Color.rgb(31, 41, 55));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                int dx = Math.round(event.getX() - lastX);
                int dy = Math.round(event.getY() - lastY);
                lastX = event.getX();
                lastY = event.getY();
                if (listener != null && (Math.abs(dx) > 1 || Math.abs(dy) > 1)) listener.onMove(dx, dy);
                return true;
            case MotionEvent.ACTION_UP:
                if (listener != null && Math.hypot(event.getX() - downX, event.getY() - downY) < dp(12)) {
                    listener.onClick();
                }
                performClick();
                return true;
            default:
                return false;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
