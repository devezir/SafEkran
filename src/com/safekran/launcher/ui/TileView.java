package com.safekran.launcher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
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
            setClickable(false);
        } else {
            setVisibility(View.VISIBLE);
            setFocusable(true);
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
            labelView.setText(context.getString(R.string.tile_spacer));
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
        setClickable(true);
        setBackgroundResource(isLightMode() ? R.drawable.bg_tile_selector_light : R.drawable.bg_tile_selector);

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
        setClickable(true);
        setBackgroundResource(isLightMode() ? R.drawable.bg_tile_selector_light : R.drawable.bg_tile_selector);

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
                float targetScale = (config != null) ? config.focusScale : 1.06f;
                if (hasFocus) {
                    animate().scaleX(targetScale).scaleY(targetScale).setDuration(120).start();
                    bringToFront();
                } else {
                    animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                }
            }
        });
    }

    public Tile getTile() {
        return tile;
    }

    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
