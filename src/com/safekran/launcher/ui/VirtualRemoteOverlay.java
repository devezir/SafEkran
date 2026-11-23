package com.safekran.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.safekran.launcher.util.UiUtils;

/**
 * Saf Ekran - Sürüklenebilir Sanal TV Kumandası Bileşeni (Draggable Virtual Remote Overlay).
 * Dokunmatik ekranlarda TV kumandası ergonomisini ve D-Pad gezintisini simüle eder.
 */
public class VirtualRemoteOverlay {

    public interface RemoteActionListener {
        void onSendKeyEvent(int keyCode);
        void onExecuteLongClick();
        void onCloseRemote();
    }

    private final Context context;
    private final RemoteActionListener listener;
    private LinearLayout remoteLayout;
    private TextView remoteTitleView;
    private long lastLongClickTimestamp = 0;

    public VirtualRemoteOverlay(Context context, RemoteActionListener listener) {
        this.context = context;
        this.listener = listener;
        initView();
    }

    private void initView() {
        int pad = UiUtils.dpToPx(context, 10);
        int btnPad = UiUtils.dpToPx(context, 6);

        remoteLayout = new LinearLayout(context);
        remoteLayout.setOrientation(LinearLayout.VERTICAL);
        remoteLayout.setPadding(pad, pad, pad, pad);
        remoteLayout.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E618181A"));
        bg.setCornerRadius(UiUtils.dpToPx(context, 14));
        bg.setStroke(UiUtils.dpToPx(context, 1), Color.parseColor("#4DFFFFFF"));
        remoteLayout.setBackground(bg);

        // 1. Üst Başlık & Kapatma & SÜRÜKLEME TUTAMACI
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, UiUtils.dpToPx(context, 6));

        remoteTitleView = new TextView(context);
        remoteTitleView.setText("🎮 TV Kumandası (Sürükle)");
        remoteTitleView.setTextColor(UiUtils.COLOR_CYAN_LIGHT);
        remoteTitleView.setTextSize(11);
        remoteTitleView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(remoteTitleView);

        TextView closeBtn = new TextView(context);
        closeBtn.setText(" ✕ ");
        closeBtn.setTextColor(Color.parseColor("#FF5252"));
        closeBtn.setTextSize(13);
        closeBtn.setPadding(btnPad, 0, btnPad, 0);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onCloseRemote();
                }
            }
        });
        headerRow.addView(closeBtn);
        remoteLayout.addView(headerRow);

        // Sürüklenebilir Panel (Draggable Gesture)
        headerRow.setOnTouchListener(new View.OnTouchListener() {
            private float initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = remoteLayout.getTranslationX();
                        initialY = remoteLayout.getTranslationY();
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        remoteLayout.setTranslationX(initialX + dx);
                        remoteLayout.setTranslationY(initialY + dy);
                        return true;
                }
                return false;
            }
        });

        // 2. D-PAD (YÖN TUŞLARI)
        LinearLayout rowUp = new LinearLayout(context);
        rowUp.setGravity(Gravity.CENTER);
        rowUp.addView(createRemoteButton("▲", KeyEvent.KEYCODE_DPAD_UP));
        remoteLayout.addView(rowUp);

        LinearLayout rowMid = new LinearLayout(context);
        rowMid.setGravity(Gravity.CENTER);
        rowMid.addView(createRemoteButton("◀", KeyEvent.KEYCODE_DPAD_LEFT));

        Button okBtn = createRemoteButton("OK", KeyEvent.KEYCODE_DPAD_CENTER);
        okBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                lastLongClickTimestamp = SystemClock.uptimeMillis();
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                v.setAlpha(0.2f);
                v.animate().alpha(1.0f).setDuration(200).start();
                if (listener != null) {
                    listener.onExecuteLongClick();
                }
                return true;
            }
        });
        rowMid.addView(okBtn);

        rowMid.addView(createRemoteButton("▶", KeyEvent.KEYCODE_DPAD_RIGHT));
        remoteLayout.addView(rowMid);

        LinearLayout rowDown = new LinearLayout(context);
        rowDown.setGravity(Gravity.CENTER);
        rowDown.addView(createRemoteButton("▼", KeyEvent.KEYCODE_DPAD_DOWN));
        remoteLayout.addView(rowDown);

        // 3. Renk Tuşları
        LinearLayout rowColors = new LinearLayout(context);
        rowColors.setGravity(Gravity.CENTER);
        rowColors.setPadding(0, UiUtils.dpToPx(context, 6), 0, UiUtils.dpToPx(context, 4));
        rowColors.addView(createColorRemoteButton("🔴", KeyEvent.KEYCODE_PROG_RED));
        rowColors.addView(createColorRemoteButton("🟢", KeyEvent.KEYCODE_PROG_GREEN));
        rowColors.addView(createColorRemoteButton("🟡", KeyEvent.KEYCODE_PROG_YELLOW));
        rowColors.addView(createColorRemoteButton("🔵", KeyEvent.KEYCODE_PROG_BLUE));
        remoteLayout.addView(rowColors);

        // 4. Fonksiyon Tuşları
        LinearLayout rowActions = new LinearLayout(context);
        rowActions.setGravity(Gravity.CENTER);
        rowActions.addView(createActionButton("MENÜ", KeyEvent.KEYCODE_MENU));
        rowActions.addView(createActionButton("GERİ", KeyEvent.KEYCODE_BACK));
        remoteLayout.addView(rowActions);
    }

    public void updateTitle(String text) {
        if (remoteTitleView != null) {
            remoteTitleView.setText(text != null ? text : "🎮 TV Kumandası (Sürükle)");
        }
    }

    public void attachTo(FrameLayout container) {
        if (container == null || remoteLayout == null) return;
        detachFrom(container);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END
        );
        flp.setMargins(0, 0, UiUtils.dpToPx(context, 16), UiUtils.dpToPx(context, 16));
        container.addView(remoteLayout, flp);
    }

    public void detachFrom(FrameLayout container) {
        if (container == null || remoteLayout == null) return;
        container.removeView(remoteLayout);
    }

    public View getView() {
        return remoteLayout;
    }

    private Button createRemoteButton(String text, final int keyCode) {
        final Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12);
        btn.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        btn.setFocusable(false);
        int size = UiUtils.dpToPx(context, 38);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        int m = UiUtils.dpToPx(context, 2);
        lp.setMargins(m, m, m, m);
        btn.setLayoutParams(lp);
        attachKeyClick(btn, keyCode);
        return btn;
    }

    private Button createColorRemoteButton(String text, final int keyCode) {
        final Button btn = new Button(context);
        btn.setText(text);
        btn.setTextSize(12);
        btn.setBackgroundColor(Color.parseColor("#26FFFFFF"));
        btn.setFocusable(false);
        int size = UiUtils.dpToPx(context, 34);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        int m = UiUtils.dpToPx(context, 2);
        lp.setMargins(m, 0, m, 0);
        btn.setLayoutParams(lp);
        attachKeyClick(btn, keyCode);
        return btn;
    }

    private Button createActionButton(String text, final int keyCode) {
        final Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(10);
        btn.setBackgroundColor(Color.parseColor("#26FFFFFF"));
        btn.setFocusable(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiUtils.dpToPx(context, 64), UiUtils.dpToPx(context, 32));
        lp.setMargins(UiUtils.dpToPx(context, 3), UiUtils.dpToPx(context, 4), UiUtils.dpToPx(context, 3), 0);
        btn.setLayoutParams(lp);
        attachKeyClick(btn, keyCode);
        return btn;
    }

    private void attachKeyClick(final Button btn, final int keyCode) {
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (SystemClock.uptimeMillis() - lastLongClickTimestamp < 800) {
                        return;
                    }
                }
                btn.setAlpha(0.4f);
                btn.animate().alpha(1.0f).setDuration(120).start();
                if (listener != null) {
                    listener.onSendKeyEvent(keyCode);
                }
            }
        });
    }
}
