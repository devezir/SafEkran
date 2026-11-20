package com.safekran.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.safekran.launcher.R;
import com.safekran.launcher.model.Tile;
import com.safekran.launcher.model.TileType;
import com.safekran.launcher.model.WorkspaceConfig;

public class TileView extends FrameLayout {

    private final Tile tile;
    private final WorkspaceConfig config;
    private final boolean isEditMode;
    private ImageView iconView;
    private TextView labelView;
    private TextClock clockView;
    private TextClock dateClock;

    public TileView(Context context, Tile tile, WorkspaceConfig config, boolean isEditMode, Drawable appIcon) {
        super(context);
        this.tile = tile;
        this.config = config;
        this.isEditMode = isEditMode;
        init(context, appIcon);
    }

    private void init(Context context, Drawable appIcon) {
        int padding = dpToPx(context, 4);
        setPadding(padding, padding, padding, padding);

        if (tile.type == TileType.SPACER) {
            initSpacerTile(context);
        } else if (tile.type == TileType.CLOCK) {
            initClockTile(context);
        } else {
            initActionOrAppTile(context, appIcon);
        }

        setupFocusBehavior();
    }

    private boolean isLightMode() {
        return config != null && "LIGHT".equals(config.themeMode);
    }

    private void initSpacerTile(Context context) {
        if (!isEditMode) {
            setVisibility(View.INVISIBLE);
            setFocusable(false);
            setFocusableInTouchMode(false);
            setClickable(false);
        } else {
            setVisibility(View.VISIBLE);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setClickable(true);
            setBackgroundResource(R.drawable.bg_spacer_edit);

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setGravity(Gravity.CENTER);

            iconView = new ImageView(context);
            iconView.setImageResource(R.drawable.ic_spacer);
            if (isLightMode()) {
                iconView.setColorFilter(Color.parseColor("#555555"));
            }
            int iconSize = dpToPx(context, 26);
            container.addView(iconView, new LinearLayout.LayoutParams(iconSize, iconSize));

            labelView = new TextView(context);
            labelView.setText(tile.colSpan + "x" + tile.rowSpan + " " + context.getString(R.string.tile_spacer));
            labelView.setTextColor(isLightMode() ? Color.parseColor("#555555") : Color.parseColor("#888888"));
            labelView.setTextSize(10);
            labelView.setGravity(Gravity.CENTER);
            container.addView(labelView);

            addView(container, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
        }
    }

    private void initClockTile(Context context) {
        setVisibility(View.VISIBLE);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        applyTileBackground();

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

        boolean is24 = (config != null) ? config.is24Hour : true;
        boolean showDate = (config != null) ? config.showDate : true;

        clockView = new TextClock(context);
        clockView.setFormat24Hour(is24 ? "HH:mm" : "hh:mm a");
        clockView.setFormat12Hour("hh:mm a");
        clockView.setTextColor(getTextColor());
        clockView.setTextSize(22);
        clockView.setTypeface(Typeface.DEFAULT_BOLD);
        clockView.setGravity(Gravity.CENTER);
        container.addView(clockView);

        if (showDate) {
            dateClock = new TextClock(context);
            dateClock.setFormat24Hour("d MMMM EEEE");
            dateClock.setFormat12Hour("d MMMM EEEE");
            dateClock.setTextColor(isLightMode() ? Color.parseColor("#666666") : Color.parseColor("#AAAAAA"));
            dateClock.setTextSize(10);
            dateClock.setGravity(Gravity.CENTER);
            container.addView(dateClock);
        }

        addView(container, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }

    private void initActionOrAppTile(Context context, Drawable appIcon) {
        setVisibility(View.VISIBLE);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        applyTileBackground();

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

        iconView = new ImageView(context);
        int iconSize = dpToPx(context, 34);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.bottomMargin = dpToPx(context, 2);

        if (tile.type == TileType.EDIT_MODE) {
            iconView.setImageResource(R.drawable.ic_edit);
            if (isLightMode()) {
                iconView.setColorFilter(Color.parseColor("#111111"));
            }
        } else if (tile.type == TileType.LAUNCHER_SETTINGS) {
            iconView.setImageResource(R.drawable.ic_settings);
            if (isLightMode()) {
                iconView.setColorFilter(Color.parseColor("#111111"));
            }
        } else if (tile.type == TileType.SYSTEM_SETTINGS) {
            iconView.setImageResource(R.drawable.ic_system);
            if (isLightMode()) {
                iconView.setColorFilter(Color.parseColor("#111111"));
            }
        } else if (tile.type == TileType.COLOR_RED) {
            if ("APP".equals(tile.actionType) && appIcon != null) {
                iconView.setImageDrawable(appIcon);
            } else {
                iconView.setImageResource(R.drawable.ic_color_red);
            }
        } else if (tile.type == TileType.COLOR_GREEN) {
            if ("APP".equals(tile.actionType) && appIcon != null) {
                iconView.setImageDrawable(appIcon);
            } else {
                iconView.setImageResource(R.drawable.ic_color_green);
            }
        } else if (tile.type == TileType.COLOR_YELLOW) {
            if ("APP".equals(tile.actionType) && appIcon != null) {
                iconView.setImageDrawable(appIcon);
            } else {
                iconView.setImageResource(R.drawable.ic_color_yellow);
            }
        } else if (tile.type == TileType.COLOR_BLUE) {
            if ("APP".equals(tile.actionType) && appIcon != null) {
                iconView.setImageDrawable(appIcon);
            } else {
                iconView.setImageResource(R.drawable.ic_color_blue);
            }
        } else {
            if (appIcon != null) {
                iconView.setImageDrawable(appIcon);
            } else {
                iconView.setImageResource(R.mipmap.ic_launcher);
            }
        }
        container.addView(iconView, iconParams);

        labelView = new TextView(context);
        labelView.setText(tile.label);
        labelView.setTextColor(getTextColor());
        labelView.setTextSize(11);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        labelView.setGravity(Gravity.CENTER);
        container.addView(labelView);

        addView(container, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }

    private void applyTileBackground() {
        String style = (config != null && config.tileStyle != null) ? config.tileStyle : "FOCUS_ONLY";
        boolean isLight = isLightMode();

        GradientDrawable focusedDrawable = new GradientDrawable();
        focusedDrawable.setShape(GradientDrawable.RECTANGLE);
        focusedDrawable.setCornerRadius(dpToPx(getContext(), 4));

        GradientDrawable normalDrawable = new GradientDrawable();
        normalDrawable.setShape(GradientDrawable.RECTANGLE);
        normalDrawable.setCornerRadius(dpToPx(getContext(), 4));

        int strokeWidthFocused = dpToPx(getContext(), 2);
        int strokeWidthNormal = dpToPx(getContext(), 1);

        if (tile.type == TileType.COLOR_RED) {
            focusedDrawable.setColor(Color.parseColor(isLight ? "#FFCDD2" : "#33D32F2F"));
            focusedDrawable.setStroke(strokeWidthFocused, Color.parseColor(isLight ? "#C62828" : "#FF5252"));

            if ("FLOATING".equals(style)) {
                normalDrawable.setColor(Color.TRANSPARENT);
            } else if ("FOCUS_ONLY".equals(style)) {
                normalDrawable.setColor(Color.parseColor(isLight ? "#FFFFFF" : "#1A1212"));
            } else { // ALWAYS
                normalDrawable.setColor(Color.parseColor(isLight ? "#FFFFFF" : "#1A1212"));
                normalDrawable.setStroke(strokeWidthNormal, Color.parseColor("#80E53935"));
            }
        } else if (tile.type == TileType.COLOR_GREEN) {
            focusedDrawable.setColor(Color.parseColor(isLight ? "#C8E6C9" : "#33388E3C"));
            focusedDrawable.setStroke(strokeWidthFocused, Color.parseColor(isLight ? "#2E7D32" : "#69F0AE"));

            if ("FLOATING".equals(style)) {
                normalDrawable.setColor(Color.TRANSPARENT);
            } else if ("FOCUS_ONLY".equals(style)) {
                normalDrawable.setColor(Color.parseColor(isLight ? "#FFFFFF" : "#121A14"));
            } else {
                normalDrawable.setColor(Color.parseColor(isLight ? "#FFFFFF" : "#121A14"));
                normalDrawable.setStroke(strokeWidthNormal, Color.parseColor("#8043A047"));
            }
        } else if (tile.type == TileType.COLOR_YELLOW) {
            focusedDrawable.setColor(Color.parseColor(isLight ? "#FFF9C4" : "#33FBC02D"));
            focusedDrawable.setStroke(strokeWidthFocused, Color.parseColor(isLight ? "#F57F17" : "#FFFF00"));

            if ("FLOATING".equals(style)) {
                normalDrawable.setColor(Color.TRANSPARENT);
            } else if ("FOCUS_ONLY".equals(style)) {
                normalDrawable.setColor(Color.parseColor(isLight ? "#FFFFFF" : "#1A1810"));
            } else {
                normalDrawable.setColor(Color.parseColor(isLight ? "#FFFFFF" : "#1A1810"));
                normalDrawable.setStroke(strokeWidthNormal, Color.parseColor("#80FBC02D"));
            }
        } else if (tile.type == TileType.COLOR_BLUE) {
            focusedDrawable.setColor(Color.parseColor(isLight ? "#BBDEFB" : "#331976D2"));
            focusedDrawable.setStroke(strokeWidthFocused, Color.parseColor(isLight ? "#0D47A1" : "#448AFF"));

            if ("FLOATING".equals(style)) {
                normalDrawable.setColor(Color.TRANSPARENT);
            } else if ("FOCUS_ONLY".equals(style)) {
                normalDrawable.setColor(Color.parseColor(isLight ? "#FFFFFF" : "#10151A"));
            } else {
                normalDrawable.setColor(Color.parseColor(isLight ? "#FFFFFF" : "#10151A"));
                normalDrawable.setStroke(strokeWidthNormal, Color.parseColor("#801E88E5"));
            }
        } else {
            // Standart Kutular (Uygulamalar, Saat, Ayarlar vs.)
            if (isLight) {
                focusedDrawable.setColor(Color.parseColor("#E6E6E6"));
                focusedDrawable.setStroke(strokeWidthFocused, Color.parseColor("#000000"));

                if ("FLOATING".equals(style)) {
                    normalDrawable.setColor(Color.TRANSPARENT);
                } else if ("FOCUS_ONLY".equals(style)) {
                    normalDrawable.setColor(Color.parseColor("#FFFFFF"));
                } else { // ALWAYS
                    normalDrawable.setColor(Color.parseColor("#FFFFFF"));
                    normalDrawable.setStroke(strokeWidthNormal, Color.parseColor("#D0D0D0"));
                }
            } else {
                focusedDrawable.setColor(Color.parseColor("#242424"));
                focusedDrawable.setStroke(strokeWidthFocused, Color.parseColor("#FFFFFF"));

                if ("FLOATING".equals(style)) {
                    normalDrawable.setColor(Color.TRANSPARENT);
                } else if ("FOCUS_ONLY".equals(style)) {
                    normalDrawable.setColor(Color.parseColor("#121212"));
                } else { // ALWAYS
                    normalDrawable.setColor(Color.parseColor("#121212"));
                    normalDrawable.setStroke(strokeWidthNormal, Color.parseColor("#26FFFFFF"));
                }
            }
        }

        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_focused}, focusedDrawable);
        sld.addState(new int[]{android.R.attr.state_selected}, focusedDrawable);
        sld.addState(new int[]{}, normalDrawable);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            setBackground(sld);
        } else {
            setBackgroundDrawable(sld);
        }
    }

    public void setTvFocused(boolean focused) {
        setSelected(focused);
        float targetScale = focused ? ((config != null) ? config.focusScale : 1.08f) : 1.0f;
        animate().scaleX(targetScale).scaleY(targetScale).setDuration(120).start();
        invalidate();
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        float targetScale = (config != null) ? config.focusScale : 1.08f;
        if (selected) {
            animate().scaleX(targetScale).scaleY(targetScale).setDuration(120).start();
        } else if (!hasFocus()) {
            animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        }
    }

    private int getTextColor() {
        if (config != null && "LIGHT".equals(config.themeMode)) {
            return Color.parseColor("#111111");
        }
        return Color.WHITE;
    }

    private void setupFocusBehavior() {
        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float targetScale = (config != null) ? config.focusScale : 1.08f;
                if (hasFocus) {
                    animate().scaleX(targetScale).scaleY(targetScale).setDuration(120).start();
                } else if (!isSelected()) {
                    animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                }
            }
        });
    }

    public Tile getTile() {
        return tile;
    }

    public void flashColorPress() {
        animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).withEndAction(new Runnable() {
            @Override
            public void run() {
                animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            }
        }).start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            updateDynamicSizes(w, h);
        }
    }

    private void updateDynamicSizes(int w, int h) {
        float density = getResources().getDisplayMetrics().density;
        float fontMultiplier = 1.0f;
        if (config != null) {
            if ("SMALL".equals(config.fontScaleMode)) fontMultiplier = 0.85f;
            else if ("LARGE".equals(config.fontScaleMode)) fontMultiplier = 1.25f;
        }

        // 1. Dinamik Simge Boyutu (Kutu yüksekliğinin %40'ı - %45'i)
        if (iconView != null && tile.type != TileType.CLOCK) {
            int targetIconSize = Math.max((int) (26 * density), Math.min((int) (64 * density), (int) (h * 0.42f)));
            ViewGroup.LayoutParams lp = iconView.getLayoutParams();
            if (lp != null && (lp.width != targetIconSize || lp.height != targetIconSize)) {
                lp.width = targetIconSize;
                lp.height = targetIconSize;
                iconView.setLayoutParams(lp);
            }
        }

        // 2. Dinamik Yazı Boyutu (TV Orantılı Tipografi)
        if (labelView != null) {
            float baseSp = Math.max(9.5f, Math.min(18f, (h / density) * 0.13f));
            float finalSp = baseSp * fontMultiplier;
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, finalSp);

            // Genişletilmiş kutularda (2 sütun ve üzeri) 2 satır metin kırılmasına izin ver
            if (tile.colSpan >= 2) {
                labelView.setSingleLine(false);
                labelView.setMaxLines(2);
            } else {
                labelView.setSingleLine(true);
                labelView.setMaxLines(1);
            }
        }

        // 3. Saat Kutusu Dinamik Tipografi
        if (clockView != null) {
            float clockSp = Math.max(16f, Math.min(36f, (h / density) * 0.28f)) * fontMultiplier;
            clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, clockSp);
        }
        if (dateClock != null) {
            float dateSp = Math.max(9f, Math.min(14f, (h / density) * 0.11f)) * fontMultiplier;
            dateClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, dateSp);
        }
    }

    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
