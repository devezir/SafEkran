package com.safekran.launcher.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Saf Ekran - Merkezi Arayüz ve Görsel Yardımcı Metodları
 * Ekran yoğunluğu (dp/px), odak efektleri ve dinamik metin boyutlandırma işlemlerini yönetir.
 */
public final class UiUtils {

    public static final int COLOR_TV_CYAN_STROKE = Color.parseColor("#00E5FF");
    public static final int COLOR_FOCUS_DARK = Color.parseColor("#37474F");
    public static final int COLOR_DARK_SURFACE = Color.parseColor("#263238");
    public static final int COLOR_CARD_DARK = Color.parseColor("#1C2A38");
    public static final int COLOR_DARK_BG = Color.parseColor("#1A2327");
    public static final int COLOR_CYAN_LIGHT = Color.parseColor("#80D8FF");
    public static final int COLOR_BLUE_LIGHT = Color.parseColor("#29B6F6");
    public static final int COLOR_BLUE_HEADER = Color.parseColor("#0288D1");
    public static final int COLOR_BLUE_NAVY = Color.parseColor("#0D47A1");
    public static final int COLOR_TEAL = Color.parseColor("#00897B");
    public static final int COLOR_TEAL_DARK = Color.parseColor("#00695C");
    public static final int COLOR_PURPLE = Color.parseColor("#5E35B1");
    public static final int COLOR_SLATE = Color.parseColor("#455A64");
    public static final int COLOR_ORANGE = Color.parseColor("#E65100");
    public static final int COLOR_AMBER = Color.parseColor("#FFD54F");
    public static final int COLOR_RED_ALERT = Color.parseColor("#E53935");
    public static final int COLOR_RED_DARK = Color.parseColor("#8B0000");
    public static final int COLOR_GREEN_SUCCESS = Color.parseColor("#4CAF50");
    public static final int COLOR_GREEN_DARK = Color.parseColor("#1B5E20");
    public static final int COLOR_GREEN_MEDIUM = Color.parseColor("#2E7D32");
    public static final int COLOR_TEXT_LIGHT_GRAY = Color.parseColor("#CFD8DC");
    public static final int COLOR_TEXT_MUTED = Color.parseColor("#B0BEC5");

    private UiUtils() {
        // Yardımcı sınıf, örneklenemez
    }

    public static Button createOptionButton(Context context, String title, String subtitle, int normalBgColor, float sScale) {
        Button btn = new Button(context);
        btn.setText(subtitle != null ? title + "\n" + subtitle : title);
        btn.setTextSize(Math.round(11 * sScale));
        btn.setTextColor(Color.WHITE);
        setupTvFocusHighlight(btn, normalBgColor, 4);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dpToPx(context, 4), 0, dpToPx(context, 6));
        btn.setLayoutParams(lp);
        btn.setPadding(dpToPx(context, 12), dpToPx(context, 10),
                dpToPx(context, 12), dpToPx(context, 10));
        btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return btn;
    }

    public static Button createTvButton(Context context, String text, int normalBgColor, int textColor, float sScale, int minHeightDp) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTextSize(Math.round(11 * Math.min(1.4f, sScale)));
        btn.setSingleLine(false);
        btn.setGravity(Gravity.CENTER);
        int padH = dpToPx(context, 12);
        int padV = dpToPx(context, 8);
        btn.setPadding(padH, padV, padH, padV);
        if (minHeightDp > 0) {
            btn.setMinHeight((int) (dpToPx(context, minHeightDp) * Math.min(1.4f, sScale)));
        }
        setupTvFocusHighlight(btn, normalBgColor, 4);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btn.setLayoutParams(lp);
        return btn;
    }

    public static void showToast(Context context, String message) {
        if (context == null || TextUtils.isEmpty(message)) return;
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void showToastLong(Context context, String message) {
        if (context == null || TextUtils.isEmpty(message)) return;
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    public static int dpToPx(Context context, float dp) {
        if (context == null) return (int) dp;
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    public static int dpToPx(Context context, int dp) {
        if (context == null) return dp;
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    public static float dpToPxFloat(Context context, float dp) {
        if (context == null) return dp;
        return dp * context.getResources().getDisplayMetrics().density;
    }

    public static GradientDrawable createRoundedDrawable(int color, float radiusPx, int strokeWidthPx, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        if (strokeWidthPx > 0 && strokeColor != 0) {
            drawable.setStroke(strokeWidthPx, strokeColor);
        }
        return drawable;
    }

    public static void setupTvFocusHighlight(final View view, final int normalColor, final int normalRadiusDp) {
        if (view == null) return;
        Context context = view.getContext();
        final int radiusPx = dpToPx(context, normalRadiusDp);
        final int strokePx = dpToPx(context, 2.5f);

        final Drawable normalDrawable = createRoundedDrawable(normalColor, radiusPx, 0, 0);
        final Drawable focusedDrawable = createRoundedDrawable(COLOR_FOCUS_DARK, radiusPx, strokePx, COLOR_TV_CYAN_STROKE);

        view.setBackground(normalDrawable);
        attachFocusAnimation(view, normalDrawable, focusedDrawable, 1.02f);
    }

    public static void setupTvFocusToggleButton(final Button btn, final boolean isSelected, final int radiusDp) {
        if (btn == null) return;
        Context context = btn.getContext();
        final int radiusPx = dpToPx(context, radiusDp);
        final int normalBg = isSelected ? Color.parseColor("#2E7D32") : Color.parseColor("#37474F");
        final int focusedBg = isSelected ? Color.parseColor("#388E3C") : Color.parseColor("#455A64");

        final GradientDrawable normalDrawable = createRoundedDrawable(normalBg, radiusPx, 
                isSelected ? dpToPx(context, 1.5f) : 0, 
                isSelected ? Color.parseColor("#81C784") : 0);
        final GradientDrawable focusedDrawable = createRoundedDrawable(focusedBg, radiusPx, 
                dpToPx(context, 2.5f), COLOR_TV_CYAN_STROKE);

        btn.setBackground(normalDrawable);
        btn.setTextColor(isSelected ? Color.WHITE : Color.parseColor("#CFD8DC"));
        btn.setTypeface(isSelected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

        attachFocusAnimation(btn, normalDrawable, focusedDrawable, 1.04f);
    }

    public static void setupTvFocusCheckBox(final CheckBox cb) {
        if (cb == null) return;
        Context context = cb.getContext();
        final Drawable focusedDrawable = createRoundedDrawable(
                Color.parseColor("#263238"), dpToPx(context, 4), dpToPx(context, 2.0f), COLOR_TV_CYAN_STROKE);

        attachFocusAnimation(cb, null, focusedDrawable, 1.02f);
    }

    public static void setupTvFocusSlider(final SeekBar sb) {
        if (sb == null) return;
        Context context = sb.getContext();
        final Drawable focusedDrawable = createRoundedDrawable(
                Color.parseColor("#1A2327"), dpToPx(context, 6), dpToPx(context, 2.0f), COLOR_TV_CYAN_STROKE);

        attachFocusAnimation(sb, null, focusedDrawable, 1.02f);
    }

    public static void attachFocusAnimation(final View view, final Drawable normalDrawable, final Drawable focusedDrawable, final float scaleFactor) {
        if (view == null) return;
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    if (focusedDrawable != null) v.setBackground(focusedDrawable);
                    v.animate().scaleX(scaleFactor).scaleY(scaleFactor).setDuration(100).start();
                } else {
                    v.setBackground(normalDrawable);
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                }
            }
        });
    }

    public static void autoFitText(TextView tv, float maxSp, float minSp, float availWidthPx) {
        if (tv == null || availWidthPx <= 0) return;
        CharSequence cs = tv.getText();
        if (TextUtils.isEmpty(cs)) return;
        String text = cs.toString();

        float density = tv.getResources().getDisplayMetrics().density;
        android.text.TextPaint paint = new android.text.TextPaint(tv.getPaint());
        float targetPx = maxSp * density;
        paint.setTextSize(targetPx);
        float textWidth = paint.measureText(text);

        if (textWidth > availWidthPx && textWidth > 0) {
            targetPx = targetPx * (availWidthPx / textWidth);
        }
        float minPx = minSp * density;
        if (targetPx < minPx) {
            targetPx = minPx;
        }
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, targetPx);
    }
}
