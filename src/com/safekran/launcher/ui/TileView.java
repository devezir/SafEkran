package com.safekran.launcher.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
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

import java.io.File;

public class TileView extends FrameLayout {

    private final Tile tile;
    private final WorkspaceConfig config;
    private ImageView iconView;
    private TextView labelView;
    private TextClock clockView;
    private LinearLayout dateContainer;
    private TextClock dateDayView;
    private LinearLayout dateTextGroup;
    private TextClock dateMonthView;
    private TextClock dateWeekView;
    private TextView infoPrimaryView;
    private TextView infoSecondaryView;
    private LinearLayout spacerContainer;

    public TileView(Context context, Tile tile, WorkspaceConfig config, Drawable appIcon) {
        super(context);
        this.tile = tile;
        this.config = config;
        init(context, appIcon);
    }

    @Deprecated
    public TileView(Context context, Tile tile, WorkspaceConfig config, boolean isEditMode, Drawable appIcon) {
        this(context, tile, config, appIcon);
    }

    private void init(Context context, Drawable appIcon) {
        int paddingH = dpToPx(context, 4);
        int paddingV = dpToPx(context, 4);
        setPadding(paddingH, paddingV, paddingH, paddingV);
        setClipChildren(false);
        setClipToPadding(false);

        if (tile.type == TileType.SPACER) {
            initSpacerTile(context);
        } else if (tile.type == TileType.CLOCK) {
            initClockTile(context);
        } else if (tile.type == TileType.DATE) {
            initDateTile(context);
        } else if (tile.type == TileType.BATTERY) {
            initBatteryTile(context);
        } else if (tile.type == TileType.RAM) {
            initRamTile(context);
        } else if (tile.type == TileType.STORAGE) {
            initStorageTile(context);
        } else {
            initActionOrAppTile(context, appIcon);
        }

        setupFocusBehavior();
    }

    private boolean isLightMode() {
        return config != null && "LIGHT".equals(config.themeMode);
    }

    private void initSpacerTile(Context context) {
        // Hayalet Odak (Ghost Focus) Mimarisi:
        // Normalde görünmez / saf siyah zemindedir. Kumanda veya dokunmatik odaklandığında neon/kesik çerçeve ile belirir.
        setVisibility(View.VISIBLE);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setClickable(true);
        setLongClickable(true);
        setBackgroundColor(Color.TRANSPARENT);

        spacerContainer = new LinearLayout(context);
        spacerContainer.setOrientation(LinearLayout.VERTICAL);
        spacerContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        spacerContainer.setClipChildren(false);
        spacerContainer.setClipToPadding(false);
        spacerContainer.setAlpha(0.0f); // Varsayılan olarak %100 hayalet

        // Üst Alan: Simge Gövdesi (weight=1 ile kutunun üst-orta bölgesini doldurur)
        FrameLayout iconWrapper = new FrameLayout(context);
        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        iconWrapper.setLayoutParams(wrapperParams);

        iconView = new ImageView(context);
        iconView.setImageResource(R.drawable.ic_spacer);
        if (isLightMode()) {
            iconView.setColorFilter(Color.parseColor("#555555"));
        }
        int iconSize = dpToPx(context, 26);
        iconWrapper.addView(iconView, new FrameLayout.LayoutParams(
                iconSize, iconSize, Gravity.CENTER));
        spacerContainer.addView(iconWrapper);

        // Alt Alan: Metin (Tabanda ferah güvenlik payı ile)
        labelView = new TextView(context);
        labelView.setText(tile.colSpan + "x" + tile.rowSpan + " " + context.getString(R.string.tile_spacer));
        labelView.setTextColor(isLightMode() ? Color.parseColor("#333333") : Color.parseColor("#CCCCCC"));
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        labelView.setGravity(Gravity.CENTER);
        labelView.setIncludeFontPadding(true);
        labelView.setPadding(0, 0, 0, dpToPx(context, 4));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spacerContainer.addView(labelView, labelParams);

        addView(spacerContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void updateSpacerGhostVisuals(boolean focused) {
        if (tile.type != TileType.SPACER || spacerContainer == null) return;
        if (focused) {
            spacerContainer.setAlpha(1.0f);
            setBackgroundResource(R.drawable.bg_spacer_edit);
        } else {
            spacerContainer.setAlpha(0.0f);
            setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void initClockTile(Context context) {
        setVisibility(View.VISIBLE);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setClickable(true);
        applyTileBackground();

        FrameLayout container = new FrameLayout(context);
        container.setClipChildren(false);
        container.setClipToPadding(false);

        boolean is24 = (config != null) ? config.is24Hour : true;

        clockView = new TextClock(context);
        String timeFormat = is24 ? "HH:mm" : "h:mm";
        clockView.setFormat24Hour(timeFormat);
        clockView.setFormat12Hour(timeFormat);
        clockView.setSingleLine(true);
        clockView.setMaxLines(1);
        clockView.setEllipsize(null);
        clockView.setTextColor(getTextColor());
        clockView.setTypeface(Typeface.DEFAULT_BOLD);
        clockView.setGravity(Gravity.CENTER);
        clockView.setIncludeFontPadding(false);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        container.addView(clockView, lp);

        addView(container, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void initDateTile(Context context) {
        setVisibility(View.VISIBLE);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setClickable(true);
        applyTileBackground();

        dateContainer = new LinearLayout(context);
        dateContainer.setOrientation(LinearLayout.VERTICAL);
        dateContainer.setGravity(Gravity.CENTER);
        dateContainer.setClipChildren(false);
        dateContainer.setClipToPadding(false);

        dateDayView = new TextClock(context);
        dateDayView.setFormat24Hour("d");
        dateDayView.setFormat12Hour("d");
        dateDayView.setSingleLine(true);
        dateDayView.setMaxLines(1);
        dateDayView.setEllipsize(null);
        dateDayView.setTextColor(getTextColor());
        dateDayView.setTypeface(Typeface.DEFAULT_BOLD);
        dateDayView.setGravity(Gravity.CENTER);
        dateDayView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateContainer.addView(dateDayView, dayParams);

        dateTextGroup = new LinearLayout(context);
        dateTextGroup.setOrientation(LinearLayout.VERTICAL);
        dateTextGroup.setGravity(Gravity.CENTER);
        dateTextGroup.setClipChildren(false);
        dateTextGroup.setClipToPadding(false);

        dateMonthView = new TextClock(context);
        dateMonthView.setFormat24Hour("MMMM");
        dateMonthView.setFormat12Hour("MMMM");
        dateMonthView.setSingleLine(true);
        dateMonthView.setMaxLines(1);
        dateMonthView.setEllipsize(TextUtils.TruncateAt.END);
        dateMonthView.setTextColor(getTextColor());
        dateMonthView.setTypeface(Typeface.DEFAULT_BOLD);
        dateMonthView.setGravity(Gravity.CENTER);
        dateMonthView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams monthParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateTextGroup.addView(dateMonthView, monthParams);

        dateWeekView = new TextClock(context);
        dateWeekView.setFormat24Hour("EEEE");
        dateWeekView.setFormat12Hour("EEEE");
        dateWeekView.setSingleLine(true);
        dateWeekView.setMaxLines(1);
        dateWeekView.setEllipsize(TextUtils.TruncateAt.END);
        dateWeekView.setTextColor(isLightMode() ? Color.parseColor("#666666") : Color.parseColor("#AAAAAA"));
        dateWeekView.setGravity(Gravity.CENTER);
        dateWeekView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams weekParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateTextGroup.addView(dateWeekView, weekParams);

        LinearLayout.LayoutParams tgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateContainer.addView(dateTextGroup, tgParams);

        addView(dateContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void initBatteryTile(Context context) {
        setVisibility(View.VISIBLE);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setClickable(true);
        applyTileBackground();

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setClipChildren(false);
        container.setClipToPadding(false);

        int level = -1, scale = -1, status = -1;
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            }
        } catch (Exception ignored) {}

        boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL);
        int pct = (level >= 0 && scale > 0) ? Math.round((level / (float) scale) * 100) : 100;

        infoPrimaryView = new TextView(context);
        String prefix = isCharging ? "⚡ " : "🔋 ";
        infoPrimaryView.setText(prefix + "%" + pct);
        infoPrimaryView.setTypeface(Typeface.DEFAULT_BOLD);
        infoPrimaryView.setTextColor(isCharging ? Color.parseColor("#4CAF50") : (pct <= 20 ? Color.parseColor("#E53935") : getTextColor()));
        infoPrimaryView.setGravity(Gravity.CENTER);
        infoPrimaryView.setSingleLine(true);
        container.addView(infoPrimaryView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        infoSecondaryView = new TextView(context);
        String secLabel = "Pil";
        if (isCharging) {
            secLabel = "Şarj Oluyor";
        } else if (tile.label != null && !tile.label.isEmpty()) {
            secLabel = tile.label.replace("🔋", "").replace("⚡", "").trim();
            if (secLabel.isEmpty()) secLabel = "Pil";
        }
        infoSecondaryView.setText(secLabel);
        infoSecondaryView.setTextColor(isLightMode() ? Color.parseColor("#666666") : Color.parseColor("#AAAAAA"));
        infoSecondaryView.setGravity(Gravity.CENTER);
        infoSecondaryView.setSingleLine(true);
        infoSecondaryView.setEllipsize(TextUtils.TruncateAt.END);
        container.addView(infoSecondaryView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        addView(container, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void updateBatteryData(int pct, boolean isCharging) {
        if (tile.type != TileType.BATTERY || infoPrimaryView == null || infoSecondaryView == null) return;
        String prefix = isCharging ? "⚡ " : "🔋 ";
        infoPrimaryView.setText(prefix + "%" + pct);
        infoPrimaryView.setTextColor(isCharging ? Color.parseColor("#4CAF50") : (pct <= 20 ? Color.parseColor("#E53935") : getTextColor()));
        String secLabel = "Pil";
        if (isCharging) {
            secLabel = "Şarj Oluyor";
        } else if (tile.label != null && !tile.label.isEmpty()) {
            secLabel = tile.label.replace("🔋", "").replace("⚡", "").trim();
            if (secLabel.isEmpty()) secLabel = "Pil";
        }
        infoSecondaryView.setText(secLabel);
    }

    public void refreshStatusData() {
        if (tile.type == TileType.BATTERY) {
            int level = -1, scale = -1, status = -1;
            try {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = getContext().registerReceiver(null, ifilter);
                if (batteryStatus != null) {
                    level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                }
            } catch (Exception ignored) {}
            boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL);
            int pct = (level >= 0 && scale > 0) ? Math.round((level / (float) scale) * 100) : 100;
            updateBatteryData(pct, isCharging);
        } else if (tile.type == TileType.RAM) {
            if (infoPrimaryView == null || infoSecondaryView == null) return;
            int usedPct = 0;
            double usedGb = 0, totalGb = 0;
            try {
                ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                if (am != null) {
                    am.getMemoryInfo(mi);
                    long totalMem = mi.totalMem;
                    long availMem = mi.availMem;
                    long usedMem = totalMem - availMem;
                    usedPct = (totalMem > 0) ? (int) Math.round((usedMem / (double) totalMem) * 100) : 0;
                    usedGb = usedMem / (1024.0 * 1024.0 * 1024.0);
                    totalGb = totalMem / (1024.0 * 1024.0 * 1024.0);
                }
            } catch (Exception ignored) {}
            infoPrimaryView.setText("RAM: %" + usedPct);
            infoPrimaryView.setTextColor(usedPct > 85 ? Color.parseColor("#E53935") : getTextColor());
            infoSecondaryView.setText(String.format(java.util.Locale.US, "%.1f / %.1f GB", usedGb, totalGb));
        } else if (tile.type == TileType.STORAGE) {
            if (infoPrimaryView == null) return;
            long freeGb = 0;
            try {
                File path = Environment.getDataDirectory();
                StatFs stat = new StatFs(path.getPath());
                long blockSize, availableBlocks;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    blockSize = stat.getBlockSizeLong();
                    availableBlocks = stat.getAvailableBlocksLong();
                } else {
                    blockSize = stat.getBlockSize();
                    availableBlocks = stat.getAvailableBlocks();
                }
                freeGb = (availableBlocks * blockSize) / (1024L * 1024L * 1024L);
            } catch (Exception ignored) {}
            infoPrimaryView.setText(freeGb + " GB Boş");
        }
    }

    private void initRamTile(Context context) {
        setVisibility(View.VISIBLE);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setClickable(true);
        applyTileBackground();

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setClipChildren(false);
        container.setClipToPadding(false);

        int usedPct = 0;
        double usedGb = 0, totalGb = 0;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am != null) {
                am.getMemoryInfo(mi);
                long totalMem = mi.totalMem;
                long availMem = mi.availMem;
                long usedMem = totalMem - availMem;
                usedPct = (totalMem > 0) ? (int) Math.round((usedMem / (double) totalMem) * 100) : 0;
                usedGb = usedMem / (1024.0 * 1024.0 * 1024.0);
                totalGb = totalMem / (1024.0 * 1024.0 * 1024.0);
            }
        } catch (Exception ignored) {}

        infoPrimaryView = new TextView(context);
        infoPrimaryView.setText("RAM: %" + usedPct);
        infoPrimaryView.setTypeface(Typeface.DEFAULT_BOLD);
        infoPrimaryView.setTextColor(usedPct > 85 ? Color.parseColor("#E53935") : getTextColor());
        infoPrimaryView.setGravity(Gravity.CENTER);
        infoPrimaryView.setSingleLine(true);
        container.addView(infoPrimaryView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        infoSecondaryView = new TextView(context);
        String detailText = String.format(java.util.Locale.US, "%.1f / %.1f GB", usedGb, totalGb);
        infoSecondaryView.setText(detailText);
        infoSecondaryView.setTextColor(isLightMode() ? Color.parseColor("#666666") : Color.parseColor("#AAAAAA"));
        infoSecondaryView.setGravity(Gravity.CENTER);
        infoSecondaryView.setSingleLine(true);
        container.addView(infoSecondaryView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        addView(container, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void initStorageTile(Context context) {
        setVisibility(View.VISIBLE);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setClickable(true);
        applyTileBackground();

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setClipChildren(false);
        container.setClipToPadding(false);

        long freeGb = 0;
        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize, availableBlocks;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                blockSize = stat.getBlockSizeLong();
                availableBlocks = stat.getAvailableBlocksLong();
            } else {
                blockSize = stat.getBlockSize();
                availableBlocks = stat.getAvailableBlocks();
            }
            freeGb = (availableBlocks * blockSize) / (1024L * 1024L * 1024L);
        } catch (Exception ignored) {}

        infoPrimaryView = new TextView(context);
        infoPrimaryView.setText(freeGb + " GB Boş");
        infoPrimaryView.setTypeface(Typeface.DEFAULT_BOLD);
        infoPrimaryView.setTextColor(getTextColor());
        infoPrimaryView.setGravity(Gravity.CENTER);
        infoPrimaryView.setSingleLine(true);
        container.addView(infoPrimaryView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        infoSecondaryView = new TextView(context);
        infoSecondaryView.setText("Depolama");
        infoSecondaryView.setTextColor(isLightMode() ? Color.parseColor("#666666") : Color.parseColor("#AAAAAA"));
        infoSecondaryView.setGravity(Gravity.CENTER);
        infoSecondaryView.setSingleLine(true);
        container.addView(infoSecondaryView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        addView(container, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void initActionOrAppTile(Context context, Drawable appIcon) {
        setVisibility(View.VISIBLE);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setClickable(true);
        applyTileBackground();

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setClipChildren(false);
        container.setClipToPadding(false);

        // 1. Üst / Gövde Alanı: Simge Alanı (Kutunun vitrin alanını doldurur, weight=1 ile esnek)
        FrameLayout iconWrapper = new FrameLayout(context);
        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        iconWrapper.setLayoutParams(wrapperParams);

        iconView = new ImageView(context);
        int iconSize = dpToPx(context, 34);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                iconSize, iconSize, Gravity.CENTER);
        iconView.setLayoutParams(iconParams);

        if (tile.type == TileType.LAUNCHER_SETTINGS) {
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
        iconWrapper.addView(iconView);
        container.addView(iconWrapper);

        // 2. Alt Taban Alanı: Metin Alanı (Tabanda ferah ve kuyruk payı garantili)
        labelView = new TextView(context);
        labelView.setText(tile.label);
        labelView.setTextColor(getTextColor());
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        labelView.setMaxLines(tile.colSpan >= 2 ? 2 : 1);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        labelView.setGravity(Gravity.CENTER);
        labelView.setIncludeFontPadding(true);
        // Harf kuyruklarının (y, g, ş, ç) kesilmesini önleyen taban dolgusu
        labelView.setPadding(dpToPx(context, 2), 0, dpToPx(context, 2), dpToPx(context, 4));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        container.addView(labelView, labelParams);

        addView(container, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
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
        updateSpacerGhostVisuals(focused);
        float targetScale = focused ? ((config != null) ? config.focusScale : 1.08f) : 1.0f;
        animate().scaleX(targetScale).scaleY(targetScale).setDuration(120).start();
        if (focused && !hasFocus()) {
            requestFocus();
        }
        invalidate();
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        updateSpacerGhostVisuals(selected);
        float targetScale = (config != null) ? config.focusScale : 1.08f;
        if (selected) {
            animate().scaleX(targetScale).scaleY(targetScale).setDuration(120).start();
        } else if (!hasFocus()) {
            animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        }
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (tile.type == TileType.SPACER) {
            updateSpacerGhostVisuals(pressed || hasFocus() || isSelected());
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
                updateSpacerGhostVisuals(hasFocus || isSelected());
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
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        if (w > 0 && h > 0) {
            updateDynamicSizes(w, h);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            updateDynamicSizes(w, h);
        }
    }

    public void refreshDynamicSizes() {
        if (getWidth() > 0 && getHeight() > 0) {
            updateDynamicSizes(getWidth(), getHeight());
        }
    }

    private void updateDynamicSizes(int w, int h) {
        float density = getResources().getDisplayMetrics().density;
        float availWDp = Math.max(10, (w - dpToPx(getContext(), 8)) / density);
        float availHDp = Math.max(10, (h - dpToPx(getContext(), 8)) / density);

        // 1. Dinamik Simge Boyutu (Kutunun üst vitrin alanını cömertçe doldurur)
        if (iconView != null && tile.type != TileType.CLOCK && tile.type != TileType.DATE &&
                tile.type != TileType.BATTERY && tile.type != TileType.RAM && tile.type != TileType.STORAGE) {
            int maxCap;
            if (tile.colSpan >= 2 && tile.rowSpan >= 2) {
                maxCap = (int) (112 * density);
            } else if (tile.colSpan >= 2 || tile.rowSpan >= 2) {
                maxCap = (int) (84 * density);
            } else {
                maxCap = (int) (58 * density);
            }

            int targetIconSize = Math.max((int) (22 * density),
                    Math.min(maxCap, (int) (Math.min(w * 0.50f, h * 0.46f))));

            ViewGroup.LayoutParams lp = iconView.getLayoutParams();
            if (lp != null && (lp.width != targetIconSize || lp.height != targetIconSize)) {
                lp.width = targetIconSize;
                lp.height = targetIconSize;
                iconView.setLayoutParams(lp);
            }
        }

        // 2. Dinamik Uygulama / Eylem Yazı Boyutu (Kutu Boyutuna Orantılı)
        if (labelView != null) {
            float baseSp = Math.max(9.0f, Math.min(16.0f, (h / density) * 0.12f));
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp);

            if (tile.colSpan >= 2) {
                labelView.setMaxLines(2);
            } else {
                labelView.setMaxLines(1);
            }
        }

        // 3. Saat Kutusu Dinamik Tipografi (Gerçek Metin Ölçümü - Paint.measureText ile Jilet Gibi Sığdırma)
        if (clockView != null) {
            float availW = Math.max(10f, w - dpToPx(getContext(), 16));
            float availH = Math.max(10f, h - dpToPx(getContext(), 12));

            android.text.TextPaint paint = new android.text.TextPaint(clockView.getPaint());

            // Yüksekliğe göre başlangıç hedef punto (kutunun dikeyde %56'sını doldurur)
            float targetPx = availH * 0.56f;
            paint.setTextSize(targetPx);

            // Ölçüm yapılacak metinler:
            boolean is24 = (config != null) ? config.is24Hour : true;
            String currentFormatted = "";
            try {
                currentFormatted = new java.text.SimpleDateFormat(is24 ? "HH:mm" : "h:mm", java.util.Locale.getDefault()).format(new java.util.Date());
            } catch (Exception ignored) {}

            String worstCase = is24 ? "23:58" : "12:58";
            float textWidth = Math.max(paint.measureText(worstCase), paint.measureText(currentFormatted));

            if (clockView.getText() != null && clockView.getText().length() > 0) {
                textWidth = Math.max(textWidth, paint.measureText(clockView.getText().toString()));
            }

            // Metin kutudan taşıyorsa pikseli pikseline tam orantılı küçült
            if (textWidth > availW && textWidth > 0) {
                targetPx = targetPx * (availW / textWidth);
            }

            float minPx = 12 * density;
            float maxPx = 96 * density;
            targetPx = Math.max(minPx, Math.min(maxPx, targetPx));

            clockView.setTextSize(TypedValue.COMPLEX_UNIT_PX, targetPx);
        }

        // 4. Tarih Kutusu Dinamik Tipografi ve Adaptif Yerleşim (2x Geniş veya 1x Dar)
        if (dateContainer != null && dateDayView != null && dateMonthView != null && dateWeekView != null) {
            boolean isWide = (w >= h * 1.30f);

            if (isWide) {
                // 2x ve Üzeri Geniş Kart Modu: Sol (Gün Sayısı), Sağ (Ay ve Gün İsmi)
                if (dateContainer.getOrientation() != LinearLayout.HORIZONTAL) {
                    dateContainer.setOrientation(LinearLayout.HORIZONTAL);
                }
                if (dateTextGroup != null) {
                    dateTextGroup.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams tgLp = (LinearLayout.LayoutParams) dateTextGroup.getLayoutParams();
                    if (tgLp != null) {
                        tgLp.leftMargin = dpToPx(getContext(), 8);
                        dateTextGroup.setLayoutParams(tgLp);
                    }
                }
                dateMonthView.setGravity(Gravity.START);
                dateWeekView.setGravity(Gravity.START);
                dateWeekView.setVisibility(View.VISIBLE);

                float daySp = Math.max(16f, Math.min(68f, availHDp * 0.56f));
                float monthSp = Math.max(10f, Math.min(20f, availHDp * 0.22f));
                float weekSp = Math.max(9f, Math.min(16f, availHDp * 0.18f));

                dateDayView.setTextSize(TypedValue.COMPLEX_UNIT_SP, daySp);
                dateMonthView.setTextSize(TypedValue.COMPLEX_UNIT_SP, monthSp);
                dateWeekView.setTextSize(TypedValue.COMPLEX_UNIT_SP, weekSp);
            } else {
                // 1x Dar Kart Modu: Üstte Gün Sayısı ("20"), Altta Ay ("Kasım"), Alan yeterse Gün Adı ("Cuma")
                if (dateContainer.getOrientation() != LinearLayout.VERTICAL) {
                    dateContainer.setOrientation(LinearLayout.VERTICAL);
                }
                if (dateTextGroup != null) {
                    dateTextGroup.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams tgLp = (LinearLayout.LayoutParams) dateTextGroup.getLayoutParams();
                    if (tgLp != null) {
                        tgLp.leftMargin = 0;
                        dateTextGroup.setLayoutParams(tgLp);
                    }
                }
                dateMonthView.setGravity(Gravity.CENTER);
                dateWeekView.setGravity(Gravity.CENTER);

                if (availHDp < 68) {
                    dateWeekView.setVisibility(View.GONE);
                    float daySp = Math.max(11f, Math.min(30f, Math.min(availHDp * 0.42f, availWDp / 1.7f)));
                    float monthSp = Math.max(8.5f, Math.min(14f, Math.min(availHDp * 0.22f, availWDp / 3.8f)));
                    dateDayView.setTextSize(TypedValue.COMPLEX_UNIT_SP, daySp);
                    dateMonthView.setTextSize(TypedValue.COMPLEX_UNIT_SP, monthSp);
                } else {
                    dateWeekView.setVisibility(View.VISIBLE);
                    float daySp = Math.max(11f, Math.min(28f, Math.min(availHDp * 0.30f, availWDp / 1.8f)));
                    float monthSp = Math.max(8f, Math.min(12.5f, Math.min(availHDp * 0.16f, availWDp / 4.2f)));
                    float weekSp = Math.max(7.5f, Math.min(11f, Math.min(availHDp * 0.14f, availWDp / 4.5f)));
                    dateDayView.setTextSize(TypedValue.COMPLEX_UNIT_SP, daySp);
                    dateMonthView.setTextSize(TypedValue.COMPLEX_UNIT_SP, monthSp);
                    dateWeekView.setTextSize(TypedValue.COMPLEX_UNIT_SP, weekSp);
                }
            }
        }

        // 5. Bilgi ve Durum Kutuları (Pil, RAM, Depolama) Dinamik Tipografi
        if (infoPrimaryView != null) {
            float primSp = Math.max(11f, Math.min(26f, Math.min(availHDp * 0.28f, availWDp / 4.8f)));
            infoPrimaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, primSp);
        }
        if (infoSecondaryView != null) {
            float secSp = Math.max(8.5f, Math.min(14f, Math.min(availHDp * 0.16f, availWDp / 5.5f)));
            infoSecondaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, secSp);
        }
    }

    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
