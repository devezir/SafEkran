package com.safekran.launcher.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Saf Ekran - Merkezi Arayüz ve Görsel Yardımcı Metodları
 * Ekran yoğunluğu (dp/px), odak efektleri ve dinamik metin boyutlandırma işlemlerini yönetir.
 */
public final class UiUtils {

    public static final int COLOR_TV_CYAN_STROKE = Color.parseColor("#00E5FF");
    public static final int COLOR_FOCUS_DARK = Color.parseColor("#37474F");

    private UiUtils() {
        // Yardımcı sınıf, örneklenemez
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
