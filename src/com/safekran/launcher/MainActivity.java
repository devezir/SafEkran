package com.safekran.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.safekran.launcher.data.AppInfo;
import com.safekran.launcher.data.AppScanner;
import com.safekran.launcher.data.WorkspaceStorage;
import com.safekran.launcher.model.Tile;
import com.safekran.launcher.model.TileType;
import com.safekran.launcher.model.WorkspaceConfig;
import com.safekran.launcher.ui.TileView;
import com.safekran.launcher.ui.WorkspaceView;
import com.safekran.launcher.util.ApkProvider;
import com.safekran.launcher.util.IntentUtils;
import com.safekran.launcher.util.UiUtils;
import com.safekran.launcher.util.UpdateManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private WorkspaceView workspaceView;
    private WorkspaceConfig config;
    private FrameLayout rootContainer;
    private View virtualRemoteView = null;
    private final Map<String, Drawable> iconCache = new HashMap<String, Drawable>();

    // Düzenleme modunda taşınan kutucuk durumu
    private Tile selectedMovingTile = null;

    // Sanal kumanda başlık göstergesi (Mini OSD)
    private TextView remoteTitleView = null;

    // Canlı Pil Dinleyicisi (Ekran açıkken anlık güncelleme)
    private BroadcastReceiver batteryReceiver = null;

    // Uzun basma sonrası artçı tıklamaları yutan emniyet damgası (Debounce Guard)
    private long lastLongClickTimestamp = 0;

    // Çift tıklama ve kuyruk yığılmasını önleyen emniyet damgası
    private long lastTileClickTimestamp = 0;

    // Başlatıcı Ayarları penceresinin tekil örneği (Çift açılmayı engeller)
    private AlertDialog launcherSettingsDialog = null;
    private final Handler settingsScaleHandler = new Handler(Looper.getMainLooper());
    private boolean isTrackingScaleTouch = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Saf tam ekran & bildirim çubuğunu gizleme
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUI();

        // Uygulama simgelerini önbelleğe al
        List<AppInfo> apps = AppScanner.scanAllApps(this);
        for (AppInfo ai : apps) {
            iconCache.put(ai.packageName, ai.icon);
        }

        // Yapılandırmayı yükle
        config = WorkspaceStorage.loadWorkspace(this);

        workspaceView = new WorkspaceView(this);
        workspaceView.setConfig(config, iconCache);

        rootContainer = new FrameLayout(this);
        rootContainer.addView(workspaceView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(rootContainer);

        setupTileClickListeners();
        updateVirtualRemoteOverlay();
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideSystemUI();
        if (workspaceView != null) {
            workspaceView.requestLayout();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        registerBatteryReceiver();
        if (workspaceView != null) {
            workspaceView.refreshStatusTiles();
            workspaceView.playTurnstileAnimation();
        }
        checkPendingUpdateOnResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        settingsScaleHandler.removeCallbacksAndMessages(null);
        isTrackingScaleTouch = false;
        unregisterBatteryReceiver();
    }

    private void registerBatteryReceiver() {
        if (batteryReceiver != null) return;
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) return;
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL);
                int pct = (level >= 0 && scale > 0) ? Math.round((level / (float) scale) * 100) : 100;
                if (workspaceView != null) {
                    workspaceView.updateBatteryStatus(pct, isCharging);
                }
            }
        };
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(batteryReceiver, filter);
            }
        } catch (Exception ignored) {}
    }

    private void unregisterBatteryReceiver() {
        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (Exception ignored) {}
            batteryReceiver = null;
        }
    }

    private void setupTileClickListeners() {
        int count = workspaceView.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = workspaceView.getChildAt(i);
            if (child instanceof TileView) {
                final TileView tv = (TileView) child;
                final Tile tile = tv.getTile();

                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onTileClicked(tile, tv);
                    }
                });

                tv.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        lastLongClickTimestamp = SystemClock.uptimeMillis();
                        onTileLongClicked(tile);
                        return true;
                    }
                });
            }
        }
    }

    private void onTileClicked(Tile tile, TileView tv) {
        long now = SystemClock.uptimeMillis();
        if (now - lastLongClickTimestamp < 800) {
            return;
        }
        if (now - lastTileClickTimestamp < 400) {
            return;
        }
        lastTileClickTimestamp = now;

        if (workspaceView != null) {
            workspaceView.setTvSelectedTile(tv);
            updateRemoteTitleWithSelected();
        }

        // Kutu taşıma aktifse hedef kutuyla anında yer değiştir
        if (selectedMovingTile != null) {
            if (selectedMovingTile != tile) {
                swapTilePositions(selectedMovingTile, tile);
                Toast.makeText(this, selectedMovingTile.label + " ile " + tile.label + " yer değiştirdi.", Toast.LENGTH_SHORT).show();
            }
            selectedMovingTile = null;
            WorkspaceStorage.saveWorkspace(this, config);
            workspaceView.rebuildViews();
            setupTileClickListeners();
            return;
        }

        switch (tile.type) {
            case APP:
                launchApp(tile);
                break;
            case LAUNCHER_SETTINGS:
                openLauncherSettingsDialog();
                break;
            case SYSTEM_SETTINGS:
                openSystemSettings();
                break;
            case COLOR_RED:
            case COLOR_GREEN:
            case COLOR_YELLOW:
            case COLOR_BLUE:
                executeColorAction(tile, tv);
                break;
            case CLOCK:
                config.is24Hour = !config.is24Hour;
                WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                workspaceView.rebuildViews();
                setupTileClickListeners();
                break;
            case DATE:
                openCalendarApp();
                break;
            case BATTERY:
                openBatterySettings();
                break;
            case RAM:
                openRamSettings();
                break;
            case STORAGE:
                openStorageSettings();
                break;
            case NETWORK:
                openNetworkSettings();
                break;
            case BLUETOOTH:
                openBluetoothSettings();
                break;
            case SPACER:
                // Hayalet Odak: Kumandadan [OK] veya dokunma ile doğrudan boyutlandırma/silme menüsünü aç
                onTileLongClicked(tile);
                break;
        }
    }

    private void openNetworkSettings() {
        IntentUtils.openNetworkSettings(this);
    }

    private void openBluetoothSettings() {
        IntentUtils.openBluetoothSettings(this);
    }

    private List<AppInfo> getHiddenOrMissingApps() {
        List<AppInfo> allApps = AppScanner.scanAllApps(this);
        Map<String, Boolean> visiblePackages = new HashMap<String, Boolean>();
        if (config != null && config.tiles != null) {
            for (Tile t : config.tiles) {
                if (t.type == TileType.APP && t.packageName != null) {
                    visiblePackages.put(t.packageName, true);
                }
            }
        }
        List<AppInfo> hiddenApps = new java.util.ArrayList<AppInfo>();
        for (AppInfo ai : allApps) {
            if (!visiblePackages.containsKey(ai.packageName)) {
                hiddenApps.add(ai);
            }
        }
        return hiddenApps;
    }

    private List<Tile> getMissingSpecialTiles() {
        List<Tile> missing = new java.util.ArrayList<Tile>();
        boolean hasRed = false, hasGreen = false, hasYellow = false, hasBlue = false;
        boolean hasSettings = false, hasClock = false, hasDate = false;
        boolean hasBattery = false, hasRam = false, hasStorage = false;
        boolean hasNetwork = false, hasBluetooth = false, hasSysSettings = false;
        if (config != null && config.tiles != null) {
            for (Tile t : config.tiles) {
                if (t.type == TileType.COLOR_RED) hasRed = true;
                else if (t.type == TileType.COLOR_GREEN) hasGreen = true;
                else if (t.type == TileType.COLOR_YELLOW) hasYellow = true;
                else if (t.type == TileType.COLOR_BLUE) hasBlue = true;
                else if (t.type == TileType.LAUNCHER_SETTINGS) hasSettings = true;
                else if (t.type == TileType.SYSTEM_SETTINGS) hasSysSettings = true;
                else if (t.type == TileType.CLOCK) hasClock = true;
                else if (t.type == TileType.DATE) hasDate = true;
                else if (t.type == TileType.BATTERY) hasBattery = true;
                else if (t.type == TileType.RAM) hasRam = true;
                else if (t.type == TileType.STORAGE) hasStorage = true;
                else if (t.type == TileType.NETWORK) hasNetwork = true;
                else if (t.type == TileType.BLUETOOTH) hasBluetooth = true;
            }
        }
        if (!hasClock) {
            Tile c = new Tile(TileType.CLOCK, "Saat", 0, 0, 2, 1);
            missing.add(c);
        }
        if (!hasDate) {
            Tile d = new Tile(TileType.DATE, "Tarih", 0, 0, 2, 1);
            missing.add(d);
        }
        if (!hasBattery) {
            Tile bt = new Tile(TileType.BATTERY, "Pil", 0, 0, 1, 1);
            missing.add(bt);
        }
        if (!hasRam) {
            Tile rm = new Tile(TileType.RAM, "RAM", 0, 0, 1, 1);
            missing.add(rm);
        }
        if (!hasStorage) {
            Tile stg = new Tile(TileType.STORAGE, "Depolama", 0, 0, 1, 1);
            missing.add(stg);
        }
        if (!hasNetwork) {
            Tile nw = new Tile(TileType.NETWORK, "Ağ", 0, 0, 1, 1);
            missing.add(nw);
        }
        if (!hasBluetooth) {
            Tile bt = new Tile(TileType.BLUETOOTH, "Bluetooth", 0, 0, 1, 1);
            missing.add(bt);
        }
        if (!hasRed) {
            Tile r = new Tile(TileType.COLOR_RED, "🔴 Kırmızı Tuş", 0, 0, 1, 1);
            r.actionType = "NONE";
            missing.add(r);
        }
        if (!hasGreen) {
            Tile g = new Tile(TileType.COLOR_GREEN, "🟢 Gizlenenler", 0, 0, 1, 1);
            g.actionType = "HIDDEN_APPS";
            missing.add(g);
        }
        if (!hasYellow) {
            Tile y = new Tile(TileType.COLOR_YELLOW, "🟡 Tema", 0, 0, 1, 1);
            y.actionType = "THEME_TOGGLE";
            missing.add(y);
        }
        if (!hasBlue) {
            Tile b = new Tile(TileType.COLOR_BLUE, "🔵 Ayarlar", 0, 0, 1, 1);
            b.actionType = "LAUNCHER_SETTINGS";
            missing.add(b);
        }
        if (!hasSettings) {
            Tile s = new Tile(TileType.LAUNCHER_SETTINGS, "Başlatıcı Ayarları", 0, 0, 1, 1);
            missing.add(s);
        }
        if (!hasSysSettings) {
            Tile ss = new Tile(TileType.SYSTEM_SETTINGS, "Sistem Ayarları", 0, 0, 1, 1);
            missing.add(ss);
        }
        return missing;
    }

    private void openHiddenAppsDialog(final Tile anchorTile) {
        final List<AppInfo> hidden = getHiddenOrMissingApps();
        final List<Tile> missingSpecials = getMissingSpecialTiles();

        if (hidden.isEmpty() && missingSpecials.isEmpty()) {
            Toast.makeText(this, "Gizlenmiş kutu veya uygulama yok. Hepsi ana ekranda mevcut.", Toast.LENGTH_SHORT).show();
            return;
        }

        final boolean hasMultipleApps = hidden.size() > 1;
        int totalItems = (hasMultipleApps ? 1 : 0) + missingSpecials.size() + hidden.size();
        final String[] items = new String[totalItems];

        int idx = 0;
        if (hasMultipleApps) {
            items[idx++] = "➕ TÜM GİZLENEN UYGULAMALARI GERİ GETİR (" + hidden.size() + ")";
        }

        for (int i = 0; i < missingSpecials.size(); i++) {
            Tile st = missingSpecials.get(i);
            String prefix = "[ÖZEL KUTU] ";
            if (st.type == TileType.COLOR_RED || st.type == TileType.COLOR_GREEN ||
                    st.type == TileType.COLOR_YELLOW || st.type == TileType.COLOR_BLUE) {
                prefix = "[RENK KUTUSU] ";
            }
            items[idx++] = prefix + st.label + " Kutusunu Ekle";
        }

        for (int i = 0; i < hidden.size(); i++) {
            items[idx++] = hidden.get(i).label;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(anchorTile != null ? anchorTile.label + " Yanına Kutu Ekle" : "Gizlenen Kutuları / Uygulamaları Ekle");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int curIdx = 0;
                if (hasMultipleApps) {
                    if (which == curIdx) {
                        for (AppInfo ai : hidden) {
                            Tile nt = new Tile(TileType.APP, ai.label, 0, 0, 1, 1);
                            nt.packageName = ai.packageName;
                            nt.activityName = ai.activityName;
                            iconCache.put(ai.packageName, ai.icon);
                            config.tiles.add(nt);
                        }
                        saveAndRefresh("Tüm gizlenen uygulamalar geri eklendi.");
                        return;
                    }
                    curIdx++;
                }

                if (which < curIdx + missingSpecials.size()) {
                    int specialIdx = which - curIdx;
                    Tile st = missingSpecials.get(specialIdx);
                    insertTileAt(st, anchorTile);
                    saveAndRefresh(st.label + " kutusu eklendi.");
                    return;
                }
                curIdx += missingSpecials.size();

                int appIdx = which - curIdx;
                AppInfo selected = hidden.get(appIdx);
                Tile newTile = new Tile(TileType.APP, selected.label, 0, 0, 1, 1);
                newTile.packageName = selected.packageName;
                newTile.activityName = selected.activityName;
                iconCache.put(selected.packageName, selected.icon);
                insertTileAt(newTile, anchorTile);
                saveAndRefresh(selected.label + " ana ekrana eklendi.");
            }
        });
        builder.show();
    }

    private void insertTileAt(Tile newTile, Tile anchorTile) {
        if (anchorTile != null) {
            int idx = config.tiles.indexOf(anchorTile);
            if (idx >= 0 && idx < config.tiles.size()) {
                config.tiles.add(idx + 1, newTile);
            } else {
                config.tiles.add(newTile);
            }
        } else {
            config.tiles.add(newTile);
        }
    }

    private void saveAndRefresh(String toastMsg) {
        WorkspaceStorage.saveWorkspace(MainActivity.this, config);
        workspaceView.rebuildViews();
        setupTileClickListeners();
        if (toastMsg != null && !toastMsg.isEmpty()) {
            Toast.makeText(MainActivity.this, toastMsg, Toast.LENGTH_SHORT).show();
        }
    }

    private void executeColorAction(Tile tile, TileView tv) {
        if (tv != null) {
            tv.flashColorPress();
        }
        if (tile == null) return;

        String action = (tile.actionType != null) ? tile.actionType : "NONE";

        if ("APP".equals(action)) {
            if (tile.packageName != null && !tile.packageName.isEmpty()) {
                launchApp(tile);
            } else {
                Toast.makeText(this, tile.label + " için henüz bir uygulama atanmamış. Kutuya uzun basarak atayabilirsiniz.", Toast.LENGTH_SHORT).show();
            }
        } else if ("HIDDEN_APPS".equals(action)) {
            openHiddenAppsDialog(null);
        } else if ("THEME_TOGGLE".equals(action)) {
            toggleThemeQuickly();
        } else if ("LAUNCHER_SETTINGS".equals(action)) {
            openLauncherSettingsDialog();
        } else if ("SYSTEM_SETTINGS".equals(action)) {
            openSystemSettings();
        } else {
            Toast.makeText(this, tile.label + " için işlem atanmamış. Kutuya uzun basarak işlem atayabilirsiniz.", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleThemeQuickly() {
        if ("LIGHT".equals(config.themeMode)) {
            config.themeMode = "BLACK";
            Toast.makeText(this, "🌙 Saf Siyah Tema", Toast.LENGTH_SHORT).show();
        } else {
            config.themeMode = "LIGHT";
            Toast.makeText(this, "☀️ Aydınlık Tema", Toast.LENGTH_SHORT).show();
        }
        WorkspaceStorage.saveWorkspace(this, config);
        workspaceView.rebuildViews();
        setupTileClickListeners();
    }

    private void triggerColorKey(TileType colorType) {
        Tile targetTile = null;
        TileView targetView = null;
        if (config != null && config.tiles != null) {
            for (Tile t : config.tiles) {
                if (t.type == colorType) {
                    targetTile = t;
                    break;
                }
            }
        }

        if (targetTile != null) {
            int childCount = workspaceView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = workspaceView.getChildAt(i);
                if (child instanceof TileView) {
                    TileView tv = (TileView) child;
                    if (tv.getTile() == targetTile) {
                        targetView = tv;
                        break;
                    }
                }
            }
            executeColorAction(targetTile, targetView);
        } else {
            String colorName = (colorType == TileType.COLOR_RED) ? "Kırmızı" :
                    (colorType == TileType.COLOR_GREEN) ? "Yeşil" :
                    (colorType == TileType.COLOR_YELLOW) ? "Sarı" : "Mavi";
            Toast.makeText(this, colorName + " tuşuna ait kutu ana ekranda bulunmuyor.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openColorAssignmentDialog(final Tile tile) {
        final String prefix = (tile.type == TileType.COLOR_RED) ? "🔴 " :
                (tile.type == TileType.COLOR_GREEN) ? "🟢 " :
                (tile.type == TileType.COLOR_YELLOW) ? "🟡 " : "🔵 ";

        String[] actions = new String[]{
                "📱 Bir Uygulama Ata (YouTube, Netflix vb.)...",
                "👁️ Gizlenen Uygulamalar Menüsü",
                "🌗 Hızlı Tema Değiştir (Siyah ↔ Aydınlık)",
                "⚙️ Başlatıcı Ayarları",
                "🔧 Android Sistem Ayarları",
                "🚫 İşlevi Temizle (Boş)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(tile.label + " — İşlem / Uygulama Ata");
        builder.setItems(actions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    final List<AppInfo> apps = AppScanner.scanAllApps(MainActivity.this);
                    String[] appNames = new String[apps.size()];
                    for (int i = 0; i < apps.size(); i++) {
                        appNames[i] = apps.get(i).label;
                    }
                    AlertDialog.Builder appDialog = new AlertDialog.Builder(MainActivity.this);
                    appDialog.setTitle(tile.label + " için Uygulama Seç");
                    appDialog.setItems(appNames, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int appIdx) {
                            AppInfo chosen = apps.get(appIdx);
                            tile.actionType = "APP";
                            tile.packageName = chosen.packageName;
                            tile.activityName = chosen.activityName;
                            tile.label = prefix + chosen.label;
                            iconCache.put(chosen.packageName, chosen.icon);

                            WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                            workspaceView.rebuildViews();
                            setupTileClickListeners();
                            Toast.makeText(MainActivity.this, tile.label + " atandı.", Toast.LENGTH_SHORT).show();
                        }
                    });
                    appDialog.show();
                } else if (which == 1) {
                    tile.actionType = "HIDDEN_APPS";
                    tile.packageName = "";
                    tile.label = prefix + "Gizlenenler";
                    finishColorAssignment(tile);
                } else if (which == 2) {
                    tile.actionType = "THEME_TOGGLE";
                    tile.packageName = "";
                    tile.label = prefix + "Tema";
                    finishColorAssignment(tile);
                } else if (which == 3) {
                    tile.actionType = "LAUNCHER_SETTINGS";
                    tile.packageName = "";
                    tile.label = prefix + "Ayarlar";
                    finishColorAssignment(tile);
                } else if (which == 4) {
                    tile.actionType = "SYSTEM_SETTINGS";
                    tile.packageName = "";
                    tile.label = prefix + "Sistem";
                    finishColorAssignment(tile);
                } else if (which == 5) {
                    tile.actionType = "NONE";
                    tile.packageName = "";
                    String cName = (tile.type == TileType.COLOR_RED) ? "Kırmızı" :
                            (tile.type == TileType.COLOR_GREEN) ? "Yeşil" :
                            (tile.type == TileType.COLOR_YELLOW) ? "Sarı" : "Mavi";
                    tile.label = prefix + cName;
                    finishColorAssignment(tile);
                }
            }
        });
        builder.show();
    }

    private void finishColorAssignment(Tile tile) {
        WorkspaceStorage.saveWorkspace(this, config);
        workspaceView.rebuildViews();
        setupTileClickListeners();
        Toast.makeText(this, tile.label + " güncellendi.", Toast.LENGTH_SHORT).show();
    }

    private void onTileLongClicked(final Tile tile) {
        lastLongClickTimestamp = SystemClock.uptimeMillis();

        if (config != null && config.isLayoutLocked) {
            Toast.makeText(this, "🔒 Düzen kilitli! Kutu taşımak veya düzenlemek için Ayarlar'dan kilidi açın.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (tile.type == TileType.SPACER) {
            List<String> spacerOptions = new java.util.ArrayList<String>();
            spacerOptions.add("➡️ Genişlet (+1 Sütun)");
            spacerOptions.add("⬅️ Daralt (-1 Sütun)");
            spacerOptions.add("⬆️ Yükselt (+1 Satır)");
            spacerOptions.add("⬇️ Alçalt (-1 Satır)");
            spacerOptions.add("🔁 Konumunu Değiştir (Taşı)");
            spacerOptions.add("🗑️ Boşluğu Kaldır (Sil)");

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Boşluk (" + tile.colSpan + "x" + tile.rowSpan + ") — Düzenle");
            builder.setItems(spacerOptions.toArray(new String[0]), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    int maxC = workspaceView.getCalculatedCols();
                    if (which == 0) {
                        tile.colSpan = Math.min(maxC, tile.colSpan + 1);
                        saveAndRefresh("Boşluk genişletildi (" + tile.colSpan + " sütun).");
                    } else if (which == 1) {
                        tile.colSpan = Math.max(1, tile.colSpan - 1);
                        saveAndRefresh("Boşluk daraltıldı (" + tile.colSpan + " sütun).");
                    } else if (which == 2) {
                        tile.rowSpan = Math.min(4, tile.rowSpan + 1);
                        saveAndRefresh("Boşluk yükseltildi (" + tile.rowSpan + " satır).");
                    } else if (which == 3) {
                        tile.rowSpan = Math.max(1, tile.rowSpan - 1);
                        saveAndRefresh("Boşluk alçaltıldı (" + tile.rowSpan + " satır).");
                    } else if (which == 4) {
                        selectedMovingTile = tile;
                        Toast.makeText(MainActivity.this, "Boşluk seçildi. Kumanda yön tuşlarıyla veya hedef kutuya dokunarak yerini değiştirin. Bırakmak için [OK] tuşuna basın.", Toast.LENGTH_LONG).show();
                    } else if (which == 5) {
                        config.tiles.remove(tile);
                        saveAndRefresh("Boşluk kaldırıldı.");
                    }
                }
            });
            builder.show();
            return;
        }

        final boolean isColorTile = (tile.type == TileType.COLOR_RED ||
                tile.type == TileType.COLOR_GREEN ||
                tile.type == TileType.COLOR_YELLOW ||
                tile.type == TileType.COLOR_BLUE);
        final boolean isClockTile = (tile.type == TileType.CLOCK);

        final List<AppInfo> hiddenApps = getHiddenOrMissingApps();
        String addAppOption = hiddenApps.isEmpty() ? "➕ Sağına Uygulama Ekle (Gizlenen yok)" : "➕ Sağına Uygulama Ekle (" + hiddenApps.size() + " gizlenen)";

        List<String> optionsList = new java.util.ArrayList<String>();
        if (isClockTile) {
            optionsList.add("⏰ Sistem Alarm / Saat Uygulamasını Aç");
        }
        if (isColorTile) {
            optionsList.add("🎯 İşlem / Uygulama Ata");
        }
        optionsList.add("🔁 Konumunu Değiştir (Taşı)");
        optionsList.add("➡️ Genişlet (+1 Sütun)");
        optionsList.add("⬅️ Daralt (-1 Sütun)");
        optionsList.add("⬆️ Yükselt (+1 Satır)");
        optionsList.add("⬇️ Alçalt (-1 Satır)");
        optionsList.add("🔲 Sağına Boşluk Ekle");
        optionsList.add(addAppOption);
        optionsList.add("🗑️ Kutuyu Kaldır (Gizle)");
        optionsList.add("⚙️ Başlatıcı Ayarları");

        String[] options = optionsList.toArray(new String[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(tile.label + " — Kutu İşlemleri");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int shift = 0;
                if (isClockTile) {
                    if (which == 0) {
                        openClockApp();
                        return;
                    }
                    shift++;
                }
                if (isColorTile) {
                    if (which == shift) {
                        openColorAssignmentDialog(tile);
                        return;
                    }
                    shift++;
                }

                int effectiveWhich = which - shift;
                int maxC = workspaceView.getCalculatedCols();
                if (effectiveWhich == 0) {
                    selectedMovingTile = tile;
                    Toast.makeText(MainActivity.this, tile.label + " seçildi. Kumanda yön tuşlarıyla veya hedef kutuya dokunarak yerini değiştirin. Bırakmak için [OK] tuşuna basın.", Toast.LENGTH_LONG).show();
                } else if (effectiveWhich == 1) {
                    tile.colSpan = Math.min(maxC, tile.colSpan + 1);
                    saveAndRefresh(tile.label + " genişletildi (" + tile.colSpan + " sütun).");
                } else if (effectiveWhich == 2) {
                    tile.colSpan = Math.max(1, tile.colSpan - 1);
                    saveAndRefresh(tile.label + " daraltıldı (" + tile.colSpan + " sütun).");
                } else if (effectiveWhich == 3) {
                    tile.rowSpan = Math.min(4, tile.rowSpan + 1);
                    saveAndRefresh(tile.label + " yükseltildi (" + tile.rowSpan + " satır).");
                } else if (effectiveWhich == 4) {
                    tile.rowSpan = Math.max(1, tile.rowSpan - 1);
                    saveAndRefresh(tile.label + " alçaltıldı (" + tile.rowSpan + " satır).");
                } else if (effectiveWhich == 5) {
                    int idx = config.tiles.indexOf(tile);
                    Tile spacer = new Tile(TileType.SPACER, "Boşluk", 0, 0, 1, 1);
                    if (idx >= 0 && idx < config.tiles.size()) {
                        config.tiles.add(idx + 1, spacer);
                    } else {
                        config.tiles.add(spacer);
                    }
                    saveAndRefresh("Boşluk eklendi. Kumandayla üzerine gelerek boyutlandırabilirsiniz.");
                } else if (effectiveWhich == 6) {
                    if (hiddenApps.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Gizlenmiş uygulama yok. Tüm uygulamalarınız ekranda.", Toast.LENGTH_SHORT).show();
                    } else {
                        openHiddenAppsDialog(tile);
                    }
                } else if (effectiveWhich == 7) {
                    config.tiles.remove(tile);
                    saveAndRefresh(tile.label + " gizlendi. Ayarlar veya kutu menüsünden geri getirebilirsiniz.");
                } else if (effectiveWhich == 8) {
                    openLauncherSettingsDialog();
                }
            }
        });
        builder.show();
    }

    private void swapTilePositions(Tile t1, Tile t2) {
        int idx1 = config.tiles.indexOf(t1);
        int idx2 = config.tiles.indexOf(t2);
        if (idx1 >= 0 && idx2 >= 0) {
            Collections.swap(config.tiles, idx1, idx2);
        }
    }

    private void moveTileInStream(Tile tile, int delta) {
        int currentIndex = config.tiles.indexOf(tile);
        if (currentIndex < 0) return;

        int targetIndex = currentIndex + delta;
        if (targetIndex < 0) targetIndex = 0;
        if (targetIndex >= config.tiles.size()) targetIndex = config.tiles.size() - 1;

        if (currentIndex != targetIndex) {
            config.tiles.remove(currentIndex);
            config.tiles.add(targetIndex, tile);
            WorkspaceStorage.saveWorkspace(this, config);
            workspaceView.rebuildViews();
            setupTileClickListeners();
        }
    }

    private void launchApp(Tile tile) {
        if (tile == null) return;
        launchApp(tile.packageName, tile.activityName);
    }

    private void launchApp(String packageName) {
        launchApp(packageName, null);
    }

    private void launchApp(String packageName, String activityName) {
        if (packageName == null || packageName.isEmpty()) return;
        try {
            Intent intent = null;
            if (activityName != null && !activityName.isEmpty()) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.setComponent(new android.content.ComponentName(packageName, activityName));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (getPackageManager().resolveActivity(intent, 0) == null) {
                    intent = null;
                }
            }

            if (intent == null) {
                intent = getPackageManager().getLaunchIntentForPackage(packageName);
            }

            if (intent == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                intent = getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Uygulama başlatılamadı: " + packageName, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }



    private void openClockApp() {
        IntentUtils.openClockApp(this);
    }

    private void openCalendarApp() {
        IntentUtils.openCalendarApp(this);
    }

    private void openBatterySettings() {
        IntentUtils.openBatterySettings(this);
    }

    private void openRamSettings() {
        IntentUtils.openRamSettings(this);
    }

    private void openStorageSettings() {
        IntentUtils.openStorageSettings(this);
    }

    private void openSystemSettings() {
        IntentUtils.openSystemSettings(this);
    }

    private boolean isDefaultLauncher() {
        return IntentUtils.isDefaultLauncher(this);
    }

    private boolean isXiaomiOrHyperOS() {
        return IntentUtils.isXiaomiOrHyperOS();
    }

    private void openXiaomiDefaultAppsSettings() {
        IntentUtils.openXiaomiDefaultAppsSettings(this);
    }

    private void promptSetDefaultLauncher() {
        IntentUtils.promptSetDefaultLauncher(this);
    }

    // ==========================================
    // KAPSAMLI BAŞLATICI AYARLARI MENÜSÜ
    // ==========================================

    private static class AccordionSection {
        Button header;
        LinearLayout content;
        boolean isExpanded;
    }

    private void setupTvFocusHighlight(final View view, final int normalColor, final int normalRadiusDp) {
        UiUtils.setupTvFocusHighlight(view, normalColor, normalRadiusDp);
    }

    private void setupTvFocusToggleButton(final Button btn, final boolean isSelected, final int radiusDp) {
        UiUtils.setupTvFocusToggleButton(btn, isSelected, radiusDp);
    }

    private void setupTvFocusCheckBox(final CheckBox cb) {
        UiUtils.setupTvFocusCheckBox(cb);
    }

    private void setupTvFocusSlider(final SeekBar sb) {
        UiUtils.setupTvFocusSlider(sb);
    }

    private LinearLayout addAccordionSection(LinearLayout container, final List<AccordionSection> allSections,
                                             final String title, boolean defaultExpanded, float sScale) {
        final AccordionSection section = new AccordionSection();
        section.isExpanded = defaultExpanded;

        final Button header = new Button(this);
        header.setText((defaultExpanded ? "▼ " : "► ") + title);
        header.setTextColor(Color.parseColor("#80D8FF"));
        header.setTextSize(Math.round(11 * sScale));
        header.setTypeface(Typeface.DEFAULT_BOLD);
        setupTvFocusHighlight(header, Color.parseColor("#263238"), 4);
        header.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        int padH = (int) (dpToPx(12) * Math.min(1.3f, sScale));
        int padV = (int) (dpToPx(8) * Math.min(1.3f, sScale));
        header.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hLp.topMargin = dpToPx(6);
        header.setLayoutParams(hLp);

        final LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(8));
        content.setVisibility(defaultExpanded ? View.VISIBLE : View.GONE);

        section.header = header;
        section.content = content;
        allSections.add(section);

        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean target = !section.isExpanded;
                for (AccordionSection s : allSections) {
                    s.isExpanded = false;
                    s.content.setVisibility(View.GONE);
                    String hText = s.header.getText().toString();
                    if (hText.startsWith("▼ ")) {
                        s.header.setText("► " + hText.substring(2));
                    }
                }
                section.isExpanded = target;
                section.content.setVisibility(target ? View.VISIBLE : View.GONE);
                header.setText((target ? "▼ " : "► ") + title);
            }
        });

        container.addView(header);
        container.addView(content);
        return content;
    }

    private Button createSetupOptionButton(String title, String subtitle, int normalBgColor, float sScale) {
        Button btn = new Button(this);
        btn.setText(title + "\n" + subtitle);
        btn.setTextSize(Math.round(11 * sScale));
        btn.setTextColor(Color.WHITE);
        setupTvFocusHighlight(btn, normalBgColor, 4);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dpToPx(4), 0, dpToPx(6));
        btn.setLayoutParams(lp);
        btn.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return btn;
    }

    private void showDefaultLauncherSetupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final float sScale = (config != null) ? config.settingsUiScale : 1.0f;

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        TextView titleView = new TextView(this);
        titleView.setText("🏠 Varsayılan Başlatıcı Kurulum Merkezi");
        titleView.setTextSize(Math.round(15 * sScale));
        titleView.setTextColor(Color.parseColor("#00E5FF"));
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setPadding(0, 0, 0, dpToPx(6));
        layout.addView(titleView);

        TextView descView = new TextView(this);
        descView.setText("Cihazınızın modeline ve Android sürümüne göre aşağıdaki yöntemlerden birini seçebilirsiniz:");
        descView.setTextSize(Math.round(11 * sScale));
        descView.setTextColor(Color.parseColor("#B0BEC5"));
        descView.setPadding(0, 0, 0, dpToPx(12));
        layout.addView(descView);

        // 1. Otomatik Sistem Seçicisi (Tüm Cihazlar & TV)
        Button b1 = createSetupOptionButton(
                "🚀 1. Otomatik Sistem Seçicisi (Tüm Cihazlar & TV)",
                "Evrensel Android seçicisini açar (Android 2.2 - 17)",
                Color.parseColor("#0288D1"), sScale);
        b1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                IntentUtils.triggerHomeChooser(MainActivity.this);
            }
        });
        layout.addView(b1);

        // 2. Android TV & Google TV Özel Çözümü (Önerilen)
        Button b2 = createSetupOptionButton(
                "📺 2. Android TV & Google TV Çözümü (Önerilen)",
                "Kilitli TV'lerde mevcut başlatıcının varsayılanını temizleme sayfası",
                Color.parseColor("#00897B"), sScale);
        b2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                IntentUtils.openCurrentLauncherDetails(MainActivity.this);
            }
        });
        layout.addView(b2);

        // 3. Google Resmi Onay Penceresi (Android 10 - 17)
        Button b3 = createSetupOptionButton(
                "📱 3. Google Resmi Onay Penceresi (RoleManager)",
                "Mobil telefonlar ve AOSP tabletler için resmi onay penceresi",
                Color.parseColor("#5E35B1"), sScale);
        b3.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                IntentUtils.triggerRoleManagerHome(MainActivity.this);
            }
        });
        layout.addView(b3);

        // 4. Sistem Ana Ekran Ayarları Sayfası
        Button b4 = createSetupOptionButton(
                "⚙️ 4. Sistem Ana Ekran Ayarları Sayfası",
                "Cihazın kendi Ayarlar -> Varsayılan Uygulamalar / Ana Ekran menüsü",
                Color.parseColor("#455A64"), sScale);
        b4.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                IntentUtils.openHomeSettings(MainActivity.this);
            }
        });
        layout.addView(b4);

        // 5. Xiaomi / HyperOS / MIUI Özel Sayfası
        Button b5 = createSetupOptionButton(
                "🟠 5. Xiaomi / HyperOS / MIUI Özel Sayfası",
                "Xiaomi cihazların gizlediği 'Varsayılan Başlatıcı' ayarını açar",
                Color.parseColor("#E65100"), sScale);
        b5.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                IntentUtils.openXiaomiDefaultAppsSettings(MainActivity.this);
            }
        });
        layout.addView(b5);

        // Alt Bilgi Kartı
        TextView tipView = new TextView(this);
        tipView.setText("💡 İpucu: Kumandanızdaki HOME tuşuna bastığınızda ekrana liste gelirse 'Saf Ekran'ı ve ardından 'Her Zaman'ı seçiniz.");
        tipView.setTextSize(Math.round(11 * sScale));
        tipView.setTextColor(Color.parseColor("#81C784"));
        tipView.setPadding(0, dpToPx(10), 0, dpToPx(8));
        layout.addView(tipView);

        scroll.addView(layout);
        builder.setView(scroll);
        builder.setNegativeButton("Kapat", null);
        AlertDialog d = builder.create();
        d.show();
    }

    private void openLauncherSettingsDialog() {
        openLauncherSettingsDialog(false);
    }

    private void openLauncherSettingsDialog(final boolean keepSliderFocus) {
        if (launcherSettingsDialog != null) {
            if (launcherSettingsDialog.isShowing()) {
                return;
            }
            try {
                launcherSettingsDialog.setOnDismissListener(null);
                launcherSettingsDialog.dismiss();
            } catch (Exception ignored) {}
            launcherSettingsDialog = null;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        final float sScale = (config != null) ? config.settingsUiScale : 1.0f;

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        // 1. İmza & Sürüm Başlığı
        TextView authorText = new TextView(this);
        String currentVersion = "2.22.1";
        try {
            currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        authorText.setText("Saf Ekran v" + currentVersion + " | Geliştirici: Vezir");
        authorText.setTextColor(Color.parseColor("#4CAF50"));
        authorText.setTextSize(Math.round(12 * sScale));
        authorText.setPadding(0, 0, 0, dpToPx(8));
        layout.addView(authorText);

        // 0. ARAYÜZ ÖLÇEĞİ (GÖZLÜK VE TV MENÜ BÜYÜTME - EN ÜSTTE)
        final int currentStep = Math.max(0, Math.min(33, Math.round((sScale * 100f - 85f) / 5f)));
        final int currentPct = 85 + currentStep * 5;

        LinearLayout scaleHeaderRow = new LinearLayout(this);
        scaleHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        scaleHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        scaleHeaderRow.setPadding(0, dpToPx(4), 0, dpToPx(2));

        TextView scaleHeader = new TextView(this);
        scaleHeader.setText("🔍 MENÜ ARAYÜZ ÖLÇEĞİ (GÖZLÜK & TV)");
        scaleHeader.setTextColor(Color.parseColor("#80D8FF"));
        scaleHeader.setTextSize(Math.round(11 * sScale));
        scaleHeader.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        scaleHeader.setLayoutParams(shLp);
        scaleHeaderRow.addView(scaleHeader);

        final TextView scaleValText = new TextView(this);
        scaleValText.setText("%" + currentPct);
        scaleValText.setTextColor(Color.parseColor("#00E5FF"));
        scaleValText.setTextSize(Math.round(13 * sScale));
        scaleValText.setTypeface(Typeface.DEFAULT_BOLD);
        scaleValText.setGravity(Gravity.END);
        scaleHeaderRow.addView(scaleValText);

        layout.addView(scaleHeaderRow);

        // Slider ve [-] [+] Butonları Satırı (%85 - %250)
        final LinearLayout scaleRow = new LinearLayout(this);
        scaleRow.setOrientation(LinearLayout.HORIZONTAL);
        scaleRow.setGravity(Gravity.CENTER_VERTICAL);
        scaleRow.setPadding(0, 0, 0, dpToPx(6));

        final Button minusBtn = new Button(this);
        minusBtn.setText("－");
        minusBtn.setTextSize(Math.round(14 * sScale));
        minusBtn.setTextColor(Color.WHITE);
        setupTvFocusHighlight(minusBtn, Color.parseColor("#37474F"), 4);
        int btnW = (int) (dpToPx(44) * Math.min(1.3f, sScale));
        int btnH = (int) (dpToPx(38) * Math.min(1.3f, sScale));
        LinearLayout.LayoutParams minusLp = new LinearLayout.LayoutParams(btnW, btnH);
        minusLp.rightMargin = dpToPx(4);
        minusBtn.setLayoutParams(minusLp);

        final SeekBar scaleSeekBar = new SeekBar(this);
        scaleSeekBar.setMax(33);
        scaleSeekBar.setProgress(currentStep);
        scaleSeekBar.setKeyProgressIncrement(1);
        scaleSeekBar.setFocusable(true);
        scaleSeekBar.setFocusableInTouchMode(true);
        setupTvFocusSlider(scaleSeekBar);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sbLp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
        scaleSeekBar.setLayoutParams(sbLp);

        final Button plusBtn = new Button(this);
        plusBtn.setText("＋");
        plusBtn.setTextSize(Math.round(14 * sScale));
        plusBtn.setTextColor(Color.WHITE);
        setupTvFocusHighlight(plusBtn, Color.parseColor("#37474F"), 4);
        LinearLayout.LayoutParams plusLp = new LinearLayout.LayoutParams(btnW, btnH);
        plusLp.leftMargin = dpToPx(4);
        plusBtn.setLayoutParams(plusLp);

        scaleRow.addView(minusBtn);
        scaleRow.addView(scaleSeekBar);
        scaleRow.addView(plusBtn);
        layout.addView(scaleRow);

        settingsScaleHandler.removeCallbacksAndMessages(null);
        final Runnable applyScaleRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTrackingScaleTouch) {
                    return;
                }
                int step = scaleSeekBar.getProgress();
                float newScale = (85 + step * 5) / 100f;
                if (Math.abs(config.settingsUiScale - newScale) > 0.01f) {
                    config.settingsUiScale = newScale;
                    WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                    if (launcherSettingsDialog != null) {
                        launcherSettingsDialog.setOnDismissListener(null);
                        try {
                            launcherSettingsDialog.dismiss();
                        } catch (Exception ignored) {}
                        launcherSettingsDialog = null;
                    }
                    openLauncherSettingsDialog(true);
                }
            }
        };

        scaleSeekBar.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        int cur = scaleSeekBar.getProgress();
                        if (cur < 33) {
                            scaleSeekBar.setProgress(cur + 1);
                            settingsScaleHandler.removeCallbacks(applyScaleRunnable);
                            settingsScaleHandler.postDelayed(applyScaleRunnable, 400);
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        int cur = scaleSeekBar.getProgress();
                        if (cur > 0) {
                            scaleSeekBar.setProgress(cur - 1);
                            settingsScaleHandler.removeCallbacks(applyScaleRunnable);
                            settingsScaleHandler.postDelayed(applyScaleRunnable, 400);
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        scaleSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int pct = 85 + progress * 5;
                scaleValText.setText("%" + pct);
                if (fromUser && !isTrackingScaleTouch) {
                    settingsScaleHandler.removeCallbacks(applyScaleRunnable);
                    settingsScaleHandler.postDelayed(applyScaleRunnable, 400);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                isTrackingScaleTouch = true;
                settingsScaleHandler.removeCallbacks(applyScaleRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                isTrackingScaleTouch = false;
                settingsScaleHandler.removeCallbacks(applyScaleRunnable);
                settingsScaleHandler.postDelayed(applyScaleRunnable, 150);
            }
        });

        minusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int cur = scaleSeekBar.getProgress();
                if (cur > 0) {
                    scaleSeekBar.setProgress(cur - 1);
                    settingsScaleHandler.removeCallbacks(applyScaleRunnable);
                    settingsScaleHandler.postDelayed(applyScaleRunnable, 400);
                }
            }
        });

        plusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int cur = scaleSeekBar.getProgress();
                if (cur < 33) {
                    scaleSeekBar.setProgress(cur + 1);
                    settingsScaleHandler.removeCallbacks(applyScaleRunnable);
                    settingsScaleHandler.postDelayed(applyScaleRunnable, 400);
                }
            }
        });

        // --- VARSAYILAN BAŞLATICI KARTI (KUTU GEOMETRİSİNİN HEMEN ÜSTÜNDE) ---
        final boolean isDefault = isDefaultLauncher();
        final Button defaultLauncherBtn = new Button(this);
        if (isDefault) {
            defaultLauncherBtn.setText("✅ Saf Ekran Varsayılan Başlatıcı");
            defaultLauncherBtn.setTextColor(Color.parseColor("#A5D6A7"));
            setupTvFocusHighlight(defaultLauncherBtn, Color.parseColor("#1B5E20"), 4);
        } else {
            defaultLauncherBtn.setText("🏠 Saf Ekran'ı Varsayılan Başlatıcı Yap");
            defaultLauncherBtn.setTextColor(Color.WHITE);
            setupTvFocusHighlight(defaultLauncherBtn, Color.parseColor("#E65100"), 4);
        }
        defaultLauncherBtn.setTextSize(Math.round(11 * sScale));
        LinearLayout.LayoutParams defLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (dpToPx(40) * Math.min(1.3f, sScale)));
        defLp.setMargins(0, dpToPx(4), 0, dpToPx(8));
        defaultLauncherBtn.setLayoutParams(defLp);
        defaultLauncherBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDefaultLauncherSetupDialog();
            }
        });
        layout.addView(defaultLauncherBtn);

        final List<AccordionSection> allSections = new java.util.ArrayList<AccordionSection>();

        // 1. Kutu Geometrisi (Canlı Önizleme — Varsayılan AÇIK)
        LinearLayout secGeometry = addAccordionSection(layout, allSections, "1. KUTU GEOMETRİSİ (CANLI ÖNİZLEME)", true, sScale);
        addPercentSlider(secGeometry, "Kutu Boyutu (Genişlik)", config.tileScale, 6, 20, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) { 
                config.tileScale = val; 
                workspaceView.requestLayout(); 
            }
        });
        addPercentSlider(secGeometry, "Kutu Yükseklik Oranı (%75=TV, %100=Kare)", config.tileHeightRatio, 50, 100, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) { 
                config.tileHeightRatio = val; 
                workspaceView.requestLayout(); 
            }
        });
        addPercentSlider(secGeometry, "Kutular Arası Boşluk (Gutter)", config.gutterPercent, 0, 5, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) { 
                config.gutterPercent = val; 
                workspaceView.requestLayout(); 
            }
        });

        // 2. Güvenli Alan (Overscan)
        LinearLayout secSafeArea = addAccordionSection(layout, allSections, "2. GÜVENLİ ALAN (OVERSCAN)", false, sScale);
        addPercentSlider(secSafeArea, "Üst Güvenli Alan", config.safeArea.top, 0, 20, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) { config.safeArea.top = val; workspaceView.requestLayout(); }
        });
        addPercentSlider(secSafeArea, "Alt Güvenli Alan", config.safeArea.bottom, 0, 20, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) { config.safeArea.bottom = val; workspaceView.requestLayout(); }
        });
        addPercentSlider(secSafeArea, "Sol Güvenli Alan", config.safeArea.left, 0, 20, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) { config.safeArea.left = val; workspaceView.requestLayout(); }
        });
        addPercentSlider(secSafeArea, "Sağ Güvenli Alan", config.safeArea.right, 0, 20, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) { config.safeArea.right = val; workspaceView.requestLayout(); }
        });

        // 3. Odak ve Etkileşim
        LinearLayout secFocus = addAccordionSection(layout, allSections, "3. ODAK VE ETKİLEŞİM", false, sScale);
        float currentFocusPercent = (config.focusScale - 1.0f);
        addPercentSlider(secFocus, "Odaklanınca Büyüme Oranı", currentFocusPercent, 0, 15, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) { config.focusScale = 1.0f + val; }
        });

        // 4. Çerçeve ve Tema Stili
        LinearLayout secTheme = addAccordionSection(layout, allSections, "4. ÇERÇEVE VE TEMA STİLİ", false, sScale);
        LinearLayout styleRow = new LinearLayout(this);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        addStyleButton(styleRow, "Sadece Odakta", "FOCUS_ONLY", sScale);
        addStyleButton(styleRow, "Her Zaman", "ALWAYS", sScale);
        addStyleButton(styleRow, "Yüzen", "FLOATING", sScale);
        updateButtonGroupHighlight(styleRow, config.tileStyle);
        secTheme.addView(styleRow);

        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        addThemeButton(themeRow, "Saf Siyah", "BLACK", sScale);
        addThemeButton(themeRow, "Koyu Gri", "DARK_GRAY", sScale);
        addThemeButton(themeRow, "Aydınlık", "LIGHT", sScale);
        updateButtonGroupHighlight(themeRow, config.themeMode);
        secTheme.addView(themeRow);

        CheckBox cbAnim = new CheckBox(this);
        cbAnim.setText("✨ Açılış Animasyonu (Windows Phone Süzülme)");
        cbAnim.setTextColor(Color.parseColor("#80D8FF"));
        cbAnim.setTextSize(Math.round(12 * sScale));
        cbAnim.setChecked(config.animationEnabled);
        setupTvFocusCheckBox(cbAnim);
        cbAnim.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                config.animationEnabled = isChecked;
                if (isChecked && workspaceView != null) {
                    workspaceView.playTurnstileAnimation();
                }
            }
        });
        secTheme.addView(cbAnim);

        // 5. Telefonda Kumanda Testi
        LinearLayout secRemote = addAccordionSection(layout, allSections, "5. TELEFONDA KUMANDA TESTİ", false, sScale);
        CheckBox cbRemote = new CheckBox(this);
        cbRemote.setText("🕹️ Sanal TV Kumandası (Ekranda Test Pedi)");
        cbRemote.setTextColor(Color.parseColor("#80D8FF"));
        cbRemote.setTextSize(Math.round(12 * sScale));
        cbRemote.setChecked(config.virtualRemoteEnabled);
        setupTvFocusCheckBox(cbRemote);
        cbRemote.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                config.virtualRemoteEnabled = isChecked;
                updateVirtualRemoteOverlay();
            }
        });
        secRemote.addView(cbRemote);

        // 6. Gizlenen ve Özel Kutular
        LinearLayout secHidden = addAccordionSection(layout, allSections, "6. GİZLENEN VE ÖZEL KUTULAR", false, sScale);
        Button hiddenBtn = new Button(this);
        hiddenBtn.setText("👁️ Gizlenen Uygulamaları ve Özel Kutuları Yönet");
        hiddenBtn.setTextSize(Math.round(11 * sScale));
        hiddenBtn.setTextColor(Color.WHITE);
        setupTvFocusHighlight(hiddenBtn, Color.parseColor("#00695C"), 4);
        hiddenBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openHiddenAppsDialog(null);
            }
        });
        secHidden.addView(hiddenBtn);

        // 7. Düzen İşlemleri
        LinearLayout secLayoutOps = addAccordionSection(layout, allSections, "7. DÜZEN İŞLEMLERİ", false, sScale);

        CheckBox cbLock = new CheckBox(this);
        cbLock.setText("🔒 Düzeni Kilitle (Taşıma ve Silmeyi Engelle)");
        cbLock.setTextColor(Color.parseColor("#FFD54F"));
        cbLock.setTextSize(Math.round(12 * sScale));
        cbLock.setChecked(config.isLayoutLocked);
        setupTvFocusCheckBox(cbLock);
        cbLock.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                config.isLayoutLocked = isChecked;
            }
        });
        secLayoutOps.addView(cbLock);

        Button addSpacerBtn = new Button(this);
        addSpacerBtn.setText("➕ Yeni Boşluk (Spacer) Ekle");
        addSpacerBtn.setTextSize(Math.round(11 * sScale));
        addSpacerBtn.setTextColor(Color.WHITE);
        setupTvFocusHighlight(addSpacerBtn, Color.parseColor("#37474F"), 4);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spLp.bottomMargin = dpToPx(6);
        addSpacerBtn.setLayoutParams(spLp);
        addSpacerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Tile spacer = new Tile(TileType.SPACER, "Boşluk", 0, 0, 1, 1);
                config.tiles.add(spacer);
                WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                workspaceView.rebuildViews();
                setupTileClickListeners();
                Toast.makeText(MainActivity.this, "Yeni boşluk eklendi.", Toast.LENGTH_SHORT).show();
            }
        });
        secLayoutOps.addView(addSpacerBtn);

        Button reflowBtn = new Button(this);
        reflowBtn.setText("🔄 Sıralamayı Varsayılana Sıfırla (Alfabeye Göre Sırala)");
        reflowBtn.setTextSize(Math.round(11 * sScale));
        reflowBtn.setTextColor(Color.WHITE);
        setupTvFocusHighlight(reflowBtn, Color.parseColor("#37474F"), 4);
        reflowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int cols = workspaceView.getCalculatedCols();
                List<AppInfo> apps = AppScanner.scanAllApps(MainActivity.this);
                WorkspaceStorage.reflowContinuousTiles(config, apps, cols);
                WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                workspaceView.rebuildViews();
                setupTileClickListeners();
                Toast.makeText(MainActivity.this, "Sıralama varsayılana sıfırlandı.", Toast.LENGTH_SHORT).show();
            }
        });
        secLayoutOps.addView(reflowBtn);

        // Alt Eylem Butonları
        LinearLayout actionBlock = new LinearLayout(this);
        actionBlock.setOrientation(LinearLayout.VERTICAL);
        actionBlock.setPadding(0, dpToPx(16), 0, dpToPx(8));

        Button saveBtn = new Button(this);
        saveBtn.setText("💾 Ayarları Kaydet");
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setTextSize(Math.round(13 * sScale));
        saveBtn.setTypeface(Typeface.DEFAULT_BOLD);
        setupTvFocusHighlight(saveBtn, Color.parseColor("#2E7D32"), 4);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (dpToPx(44) * Math.min(1.3f, sScale)));
        saveLp.bottomMargin = dpToPx(8);
        saveBtn.setLayoutParams(saveLp);

        LinearLayout subRow = new LinearLayout(this);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.setWeightSum(2f);

        Button backupBtn = new Button(this);
        backupBtn.setText("🔄 Yedek / Geri Yükle");
        backupBtn.setTextColor(Color.WHITE);
        backupBtn.setTextSize(Math.round(11 * sScale));
        setupTvFocusHighlight(backupBtn, Color.parseColor("#37474F"), 4);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, (int) (dpToPx(40) * Math.min(1.3f, sScale)), 1f);
        bLp.rightMargin = dpToPx(4);
        backupBtn.setLayoutParams(bLp);

        Button resetBtn = new Button(this);
        resetBtn.setText("⚠️ Fabrika Ayarları");
        resetBtn.setTextColor(Color.WHITE);
        resetBtn.setTextSize(Math.round(11 * sScale));
        setupTvFocusHighlight(resetBtn, Color.parseColor("#8B0000"), 4);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, (int) (dpToPx(40) * Math.min(1.3f, sScale)), 1f);
        rLp.leftMargin = dpToPx(4);
        resetBtn.setLayoutParams(rLp);

        subRow.addView(backupBtn);
        subRow.addView(resetBtn);

        actionBlock.addView(saveBtn);
        actionBlock.addView(subRow);

        // En altta: Güncellemeleri Denetle Butonu
        Button checkUpdateBtn = new Button(this);
        checkUpdateBtn.setText("🔄 Güncellemeleri Denetle");
        checkUpdateBtn.setTextSize(Math.round(11 * sScale));
        checkUpdateBtn.setTextColor(Color.parseColor("#80D8FF"));
        setupTvFocusHighlight(checkUpdateBtn, Color.parseColor("#1C2A38"), 4);
        LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (dpToPx(40) * Math.min(1.3f, sScale)));
        upLp.topMargin = dpToPx(8);
        checkUpdateBtn.setLayoutParams(upLp);
        checkUpdateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startUpdateCheckFlow(sScale);
            }
        });
        actionBlock.addView(checkUpdateBtn);

        layout.addView(actionBlock);

        scroll.addView(layout);
        builder.setView(scroll);

        launcherSettingsDialog = builder.create();
        final AlertDialog dialog = launcherSettingsDialog;

        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settingsScaleHandler.removeCallbacksAndMessages(null);
                WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                workspaceView.rebuildViews();
                setupTileClickListeners();
                dialog.dismiss();
            }
        });

        backupBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settingsScaleHandler.removeCallbacksAndMessages(null);
                dialog.dismiss();
                openBackupRestoreDialog();
            }
        });

        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settingsScaleHandler.removeCallbacksAndMessages(null);
                dialog.dismiss();
                config = WorkspaceStorage.generateDefaultWorkspace(MainActivity.this);
                workspaceView.setConfig(config, iconCache);
                setupTileClickListeners();
                Toast.makeText(MainActivity.this, "Fabrika ayarlarına dönüldü.", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                settingsScaleHandler.removeCallbacksAndMessages(null);
                isTrackingScaleTouch = false;
                if (launcherSettingsDialog == d) {
                    launcherSettingsDialog = null;
                    setupTileClickListeners();
                }
            }
        });

        dialog.show();

        // Kenara yaslı ve canlı görüşü engellemeyen pencere ayarları
        Window window = dialog.getWindow();
        if (window != null) {
            boolean isLandscape = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.dimAmount = 0.05f; // Saydama yakın: Arka plandaki kutuların canlı akışı %100 görünür!
            if (isLandscape) {
                wlp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
                float widthFraction = (sScale >= 1.8f) ? 0.65f : ((sScale > 1.2f) ? 0.48f : 0.40f);
                wlp.width = (int) (dm.widthPixels * widthFraction);
                wlp.height = WindowManager.LayoutParams.MATCH_PARENT;
            } else {
                wlp.gravity = Gravity.BOTTOM;
                wlp.width = WindowManager.LayoutParams.MATCH_PARENT;
                float heightFraction = (sScale >= 1.8f) ? 0.95f : ((sScale > 1.2f) ? 0.85f : 0.70f);
                wlp.height = (int) (dm.heightPixels * heightFraction);
            }
            window.setAttributes(wlp);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#F0121212"));
            bg.setCornerRadius(dpToPx(8));
            window.setBackgroundDrawable(bg);
        }

        if (keepSliderFocus) {
            scaleSeekBar.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (scaleSeekBar != null) {
                        scaleSeekBar.setFocusable(true);
                        scaleSeekBar.setFocusableInTouchMode(true);
                        scaleSeekBar.requestFocus();
                    }
                }
            }, 50);
        }
    }

    private void startUpdateCheckFlow(final float sScale) {
        final AlertDialog checkDialog = new AlertDialog.Builder(this).create();
        LinearLayout checkLayout = new LinearLayout(this);
        checkLayout.setOrientation(LinearLayout.HORIZONTAL);
        checkLayout.setGravity(Gravity.CENTER_VERTICAL);
        checkLayout.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));

        ProgressBar spinner = new ProgressBar(this);
        checkLayout.addView(spinner);

        TextView checkMsg = new TextView(this);
        checkMsg.setText("   Güncellemeler denetleniyor...");
        checkMsg.setTextColor(Color.WHITE);
        checkMsg.setTextSize(Math.round(13 * sScale));
        checkLayout.addView(checkMsg);

        checkDialog.setView(checkLayout);
        checkDialog.setCancelable(false);
        checkDialog.show();

        UpdateManager.checkForUpdate(this, new UpdateManager.CheckCallback() {
            @Override
            public void onSuccess(final UpdateManager.UpdateInfo info) {
                if (checkDialog.isShowing()) checkDialog.dismiss();

                if (!info.hasUpdate) {
                    AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                    alert.setTitle("✅ En Güncel Sürümdesiniz");
                    alert.setMessage("Mevcut Sürüm: v" + info.currentVersionName + " (Yapı: " + info.currentVersionCode + ")\n\nSaf Ekran en güncel sürümdedir.");
                    alert.setPositiveButton("Tamam", null);
                    alert.show();
                    return;
                }

                // Yeni Sürüm Bulundu Diyaloğu
                final AlertDialog updateDialog = new AlertDialog.Builder(MainActivity.this).create();
                ScrollView sv = new ScrollView(MainActivity.this);
                LinearLayout uLayout = new LinearLayout(MainActivity.this);
                uLayout.setOrientation(LinearLayout.VERTICAL);
                uLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

                TextView titleView = new TextView(MainActivity.this);
                titleView.setText("🎉 Yeni Sürüm Mevcut: v" + info.remoteVersionName);
                titleView.setTextColor(Color.parseColor("#4CAF50"));
                titleView.setTextSize(Math.round(15 * sScale));
                titleView.setTypeface(Typeface.DEFAULT_BOLD);
                titleView.setPadding(0, 0, 0, dpToPx(6));
                uLayout.addView(titleView);

                TextView versionSub = new TextView(MainActivity.this);
                String sizeStr = info.fileSize > 0 ? " (" + (info.fileSize / 1024) + " KB)" : "";
                versionSub.setText("Kurulu: v" + info.currentVersionName + " ➔ Yeni: v" + info.remoteVersionName + sizeStr);
                versionSub.setTextColor(Color.parseColor("#80D8FF"));
                versionSub.setTextSize(Math.round(11 * sScale));
                versionSub.setPadding(0, 0, 0, dpToPx(10));
                uLayout.addView(versionSub);

                if (info.releaseNotes != null && !info.releaseNotes.trim().isEmpty()) {
                    TextView notesTitle = new TextView(MainActivity.this);
                    notesTitle.setText("📋 Sürüm Notları / Yenilikler:");
                    notesTitle.setTextColor(Color.parseColor("#FFD54F"));
                    notesTitle.setTextSize(Math.round(12 * sScale));
                    notesTitle.setTypeface(Typeface.DEFAULT_BOLD);
                    notesTitle.setPadding(0, 0, 0, dpToPx(4));
                    uLayout.addView(notesTitle);

                    TextView notesBody = new TextView(MainActivity.this);
                    notesBody.setText(info.releaseNotes.trim());
                    notesBody.setTextColor(Color.parseColor("#CFD8DC"));
                    notesBody.setTextSize(Math.round(11 * sScale));
                    notesBody.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(10));

                    GradientDrawable notesBg = new GradientDrawable();
                    notesBg.setColor(Color.parseColor("#1C2A38"));
                    notesBg.setCornerRadius(dpToPx(6));
                    notesBody.setBackground(notesBg);
                    uLayout.addView(notesBody);
                }

                LinearLayout btnRow = new LinearLayout(MainActivity.this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setPadding(0, dpToPx(14), 0, 0);

                final Button dlBtn = new Button(MainActivity.this);
                dlBtn.setText("📥 Hemen İndir ve Kur");
                dlBtn.setTextSize(Math.round(11 * sScale));
                dlBtn.setTextColor(Color.WHITE);
                setupTvFocusToggleButton(dlBtn, true, 4);
                LinearLayout.LayoutParams dlLp = new LinearLayout.LayoutParams(0, (int) (dpToPx(42) * Math.min(1.3f, sScale)), 1.2f);
                dlLp.rightMargin = dpToPx(4);
                dlBtn.setLayoutParams(dlLp);

                Button laterBtn = new Button(MainActivity.this);
                laterBtn.setText("Daha Sonra");
                laterBtn.setTextSize(Math.round(11 * sScale));
                laterBtn.setTextColor(Color.parseColor("#CFD8DC"));
                setupTvFocusHighlight(laterBtn, Color.parseColor("#37474F"), 4);
                LinearLayout.LayoutParams ltLp = new LinearLayout.LayoutParams(0, (int) (dpToPx(42) * Math.min(1.3f, sScale)), 0.8f);
                laterBtn.setLayoutParams(ltLp);

                btnRow.addView(dlBtn);
                btnRow.addView(laterBtn);
                uLayout.addView(btnRow);

                sv.addView(uLayout);
                updateDialog.setView(sv);

                updateDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        dlBtn.requestFocus();
                    }
                });

                dlBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        updateDialog.dismiss();
                        if (!UpdateManager.hasInstallPermission(MainActivity.this)) {
                            showInstallPermissionGuideDialog(info, sScale);
                        } else {
                            startDownloadFlow(info, sScale);
                        }
                    }
                });

                laterBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        updateDialog.dismiss();
                    }
                });

                updateDialog.show();
            }

            @Override
            public void onError(final String message) {
                if (checkDialog.isShowing()) checkDialog.dismiss();
                AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                alert.setTitle("⚠️ Güncelleme Denetlenemedi");
                alert.setMessage("Güncelleme sunucusuna bağlanırken hata oluştu:\n" + message + "\n\nLütfen internet bağlantınızı kontrol edip tekrar deneyin.");
                alert.setPositiveButton("Tamam", null);
                alert.show();
            }
        });
    }

    private void showInstallPermissionGuideDialog(final UpdateManager.UpdateInfo info, final float sScale) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚠️ Kurulum İzni Gerekli");
        builder.setMessage("Saf Ekran güncellemelerini kurabilmek için sonraki ekranda 'Bu kaynaktan izin ver' anahtarını açmanız gerekmektedir.\n\nİzni açıp geri döndüğünüzde indirme ve kurulum otomatik olarak başlayacaktır.");
        builder.setPositiveButton("İzin Ver ve Devam Et", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                getSharedPreferences("safekran_update", Context.MODE_PRIVATE).edit()
                        .putBoolean("pending_install_flow", true)
                        .putInt("pending_remote_vc", info.remoteVersionCode)
                        .putString("pending_remote_vn", info.remoteVersionName)
                        .putString("pending_dl_url", info.downloadUrl)
                        .apply();
                UpdateManager.requestInstallPermission(MainActivity.this);
            }
        });
        builder.setNegativeButton("Vazgeç", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void checkPendingUpdateOnResume() {
        android.content.SharedPreferences sp = getSharedPreferences("safekran_update", Context.MODE_PRIVATE);
        boolean pending = sp.getBoolean("pending_install_flow", false);
        if (!pending) return;

        if (UpdateManager.hasInstallPermission(this)) {
            sp.edit().remove("pending_install_flow").apply();

            int remoteVc = sp.getInt("pending_remote_vc", 0);
            String remoteVn = sp.getString("pending_remote_vn", "");
            String dlUrl = sp.getString("pending_dl_url", "");
            float scale = (config != null) ? config.settingsUiScale : 1.0f;

            File apkFile = ApkProvider.getUpdateApkFile(this);
            if (UpdateManager.isApkReadyAndValid(this, remoteVc)) {
                Toast.makeText(this, "Kurulum izni verildi! Güncelleme başlatılıyor...", Toast.LENGTH_SHORT).show();
                UpdateManager.installApk(this, apkFile);
            } else if (dlUrl != null && !dlUrl.isEmpty()) {
                UpdateManager.UpdateInfo info = new UpdateManager.UpdateInfo(
                        true, 0, "", remoteVc, remoteVn, "", "", dlUrl, 0);
                Toast.makeText(this, "Kurulum izni verildi! İndirme başlatılıyor...", Toast.LENGTH_SHORT).show();
                startDownloadFlow(info, scale);
            }
        }
    }

    private void startDownloadFlow(final UpdateManager.UpdateInfo info, final float sScale) {
        File existingFile = ApkProvider.getUpdateApkFile(this);
        if (UpdateManager.isApkReadyAndValid(this, info.remoteVersionCode)) {
            Toast.makeText(this, "Güncelleme paketi hazır. Kurulum başlatılıyor...", Toast.LENGTH_SHORT).show();
            UpdateManager.installApk(this, existingFile);
            return;
        }

        final AlertDialog downloadDialog = new AlertDialog.Builder(this).create();
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(18), dpToPx(18), dpToPx(18), dpToPx(18));

        TextView dlTitle = new TextView(this);
        dlTitle.setText("Saf Ekran v" + info.remoteVersionName + " İndiriliyor...");
        dlTitle.setTextColor(Color.WHITE);
        dlTitle.setTextSize(Math.round(13 * sScale));
        dlTitle.setTypeface(Typeface.DEFAULT_BOLD);
        dlTitle.setPadding(0, 0, 0, dpToPx(10));
        layout.addView(dlTitle);

        final ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setIndeterminate(false);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(18));
        pbLp.bottomMargin = dpToPx(8);
        progressBar.setLayoutParams(pbLp);
        layout.addView(progressBar);

        final TextView statusText = new TextView(this);
        statusText.setText("Sunucuya bağlanılıyor...");
        statusText.setTextColor(Color.parseColor("#80D8FF"));
        statusText.setTextSize(Math.round(11 * sScale));
        statusText.setGravity(Gravity.CENTER);
        layout.addView(statusText);

        downloadDialog.setView(layout);
        downloadDialog.setCancelable(false);
        downloadDialog.show();

        UpdateManager.downloadUpdate(this, info.downloadUrl, new UpdateManager.DownloadCallback() {
            @Override
            public void onProgress(final int percent, final long downloadedBytes, final long totalBytes) {
                if (percent >= 0) {
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(percent);
                    String dKb = String.format(Locale.US, "%.0f", downloadedBytes / 1024f);
                    String tKb = totalBytes > 0 ? String.format(Locale.US, "%.0f", totalBytes / 1024f) : "?";
                    statusText.setText("%" + percent + " (" + dKb + " KB / " + tKb + " KB)");
                } else {
                    progressBar.setIndeterminate(true);
                    statusText.setText((downloadedBytes / 1024) + " KB indirildi...");
                }
            }

            @Override
            public void onCompleted(final File apkFile) {
                if (downloadDialog.isShowing()) downloadDialog.dismiss();
                Toast.makeText(MainActivity.this, "İndirme tamamlandı! Kurulum başlatılıyor...", Toast.LENGTH_SHORT).show();
                UpdateManager.installApk(MainActivity.this, apkFile);
            }

            @Override
            public void onError(final String message) {
                if (downloadDialog.isShowing()) downloadDialog.dismiss();
                AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                alert.setTitle("❌ İndirme Başarısız");
                alert.setMessage("APK indirilirken bir sorun oluştu:\n" + message);
                alert.setPositiveButton("Tekrar Dene", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startDownloadFlow(info, sScale);
                    }
                });
                alert.setNegativeButton("Kapat", null);
                alert.show();
            }
        });
    }

    private void addSectionHeader(LinearLayout layout, String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(Color.parseColor("#80D8FF"));
        header.setTextSize(12);
        header.setPadding(0, 20, 0, 6);
        layout.addView(header);
    }

    private void updateButtonGroupHighlight(LinearLayout row, String selectedTag) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof Button) {
                Button b = (Button) child;
                boolean isSelected = selectedTag != null && selectedTag.equals(b.getTag());
                setupTvFocusToggleButton(b, isSelected, 4);
            }
        }
    }

    private void addStyleButton(final LinearLayout row, String text, final String style, float sScale) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTag(style);
        btn.setTextSize(Math.round(10 * sScale));
        int padH = (int) (dpToPx(4) * Math.min(1.3f, sScale));
        int padV = (int) (dpToPx(6) * Math.min(1.3f, sScale));
        btn.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(4));
        btn.setLayoutParams(params);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                config.tileStyle = style;
                updateButtonGroupHighlight(row, style);
                workspaceView.rebuildViews();
                setupTileClickListeners();
                Toast.makeText(MainActivity.this, "Çerçeve Stili: " + style, Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(btn);
    }

    private void addThemeButton(final LinearLayout row, String text, final String mode, float sScale) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTag(mode);
        btn.setTextSize(Math.round(11 * sScale));
        int padH = (int) (dpToPx(4) * Math.min(1.3f, sScale));
        int padV = (int) (dpToPx(6) * Math.min(1.3f, sScale));
        btn.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(4));
        btn.setLayoutParams(params);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                config.themeMode = mode;
                updateButtonGroupHighlight(row, mode);
                workspaceView.rebuildViews();
                setupTileClickListeners();
                Toast.makeText(MainActivity.this, "Tema: " + mode, Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(btn);
    }

    private interface ValueCallback {
        void onValueChanged(float val);
    }

    private void addPercentSlider(LinearLayout layout, final String label, float initialVal, final int min, final int max, float sScale, final ValueCallback callback) {
        final TextView labelView = new TextView(this);
        int percent = Math.round(initialVal * 100);
        labelView.setText(label + ": %" + percent);
        labelView.setTextColor(Color.parseColor("#CCCCCC"));
        labelView.setTextSize(Math.round(12 * sScale));
        labelView.setPadding(0, dpToPx(8), 0, dpToPx(2));
        layout.addView(labelView);

        SeekBar seekBar = new SeekBar(this);
        setupTvFocusSlider(seekBar);
        seekBar.setMax(max - min);
        seekBar.setProgress(Math.max(0, percent - min));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int actualVal = min + progress;
                labelView.setText(label + ": %" + actualVal);
                callback.onValueChanged(actualVal / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        layout.addView(seekBar);
    }

    private File getDownloadsDir() {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dir != null) {
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if (dir.exists()) {
                return dir;
            }
        }
        return getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
    }

    private void openBackupRestoreDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔄 Çalışma Alanı Yedekleme & Geri Yükleme");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(12));

        TextView info = new TextView(this);
        info.setText("Yedekler cihazınızın Download (İndirilenler) klasöründe tarih ve saat etiketiyle saklanır.");
        info.setTextColor(Color.parseColor("#B0BEC5"));
        info.setTextSize(12);
        info.setPadding(0, 0, 0, dpToPx(14));
        layout.addView(info);

        // 1. Download Klasörüne Yedekle Butonu
        Button btnSaveDownload = new Button(this);
        btnSaveDownload.setText("📥 Download Klasörüne Yedekle (Tarihli)");
        btnSaveDownload.setTextColor(Color.WHITE);
        btnSaveDownload.setTypeface(Typeface.DEFAULT_BOLD);
        btnSaveDownload.setBackgroundColor(Color.parseColor("#1B5E20"));
        btnSaveDownload.setTextSize(12);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44));
        p1.bottomMargin = dpToPx(8);
        btnSaveDownload.setLayoutParams(p1);
        layout.addView(btnSaveDownload);

        // 2. Download Klasöründen Seç ve Geri Yükle Butonu
        Button btnRestoreDownload = new Button(this);
        btnRestoreDownload.setText("📂 Download'dan Seç ve Geri Yükle");
        btnRestoreDownload.setTextColor(Color.WHITE);
        btnRestoreDownload.setTypeface(Typeface.DEFAULT_BOLD);
        btnRestoreDownload.setBackgroundColor(Color.parseColor("#0D47A1"));
        btnRestoreDownload.setTextSize(12);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44));
        p2.bottomMargin = dpToPx(8);
        btnRestoreDownload.setLayoutParams(p2);
        layout.addView(btnRestoreDownload);

        // 3. Panodan / Metinle Düzenleme (B Planı)
        Button btnRawJson = new Button(this);
        btnRawJson.setText("📋 Panodan / Metin ile Yedekle");
        btnRawJson.setTextColor(Color.parseColor("#ECEFF1"));
        btnRawJson.setBackgroundColor(Color.parseColor("#37474F"));
        btnRawJson.setTextSize(11);
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(38));
        btnRawJson.setLayoutParams(p3);
        layout.addView(btnRawJson);

        builder.setView(layout);
        builder.setNegativeButton("Kapat", null);
        final AlertDialog dialog = builder.create();

        btnSaveDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                saveBackupToDownloads();
            }
        });

        btnRestoreDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                openFileRestoreChooserDialog();
            }
        });

        btnRawJson.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                openRawJsonDialog();
            }
        });

        dialog.show();
    }

    private void saveBackupToDownloads() {
        try {
            File dir = getDownloadsDir();
            if (dir == null) {
                Toast.makeText(this, "Download klasörüne erişilemedi!", Toast.LENGTH_LONG).show();
                return;
            }
            String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date());
            String fileName = "SafEkran_Yedek_" + timeStamp + ".json";
            File backupFile = new File(dir, fileName);

            String jsonStr = WorkspaceStorage.exportToJsonString(config);
            FileOutputStream fos = new FileOutputStream(backupFile);
            fos.write(jsonStr.getBytes("UTF-8"));
            fos.flush();
            fos.close();

            Toast.makeText(this, "✅ Yedek kaydedildi:\n" + fileName, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Yedek kaydedilemedi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openFileRestoreChooserDialog() {
        File dir = getDownloadsDir();
        if (dir == null || !dir.exists()) {
            Toast.makeText(this, "Download klasörü bulunamadı!", Toast.LENGTH_LONG).show();
            return;
        }

        File[] files = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File f) {
                if (!f.isFile()) return false;
                String name = f.getName().toLowerCase(Locale.US);
                return name.startsWith("safekran_yedek") && name.endsWith(".json");
            }
        });

        // Eğer SafEkran_Yedek bulunamazsa genel .json dosyalarını ara
        if (files == null || files.length == 0) {
            files = dir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    if (!f.isFile()) return false;
                    String name = f.getName().toLowerCase(Locale.US);
                    return name.endsWith(".json") && name.contains("safekran");
                }
            });
        }

        if (files == null || files.length == 0) {
            AlertDialog.Builder noFilesBuilder = new AlertDialog.Builder(this);
            noFilesBuilder.setTitle("Yedek Dosyası Bulunamadı");
            noFilesBuilder.setMessage("Download klasöründe herhangi bir SafEkran yedek dosyası (*.json) bulunamadı.\n\nKlasör: " + dir.getAbsolutePath());
            noFilesBuilder.setPositiveButton("Tamam", null);
            noFilesBuilder.show();
            return;
        }

        // En yeni dosyadan en eskiye sırala
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return Long.compare(f2.lastModified(), f1.lastModified());
            }
        });

        final File[] sortedFiles = files;
        String[] displayItems = new String[sortedFiles.length];
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

        for (int i = 0; i < sortedFiles.length; i++) {
            File f = sortedFiles[i];
            long sizeKb = Math.max(1, f.length() / 1024);
            String dateFormatted = sdf.format(new Date(f.lastModified()));
            displayItems[i] = "📄 " + f.getName() + "\n     " + dateFormatted + "  (" + sizeKb + " KB)";
        }

        AlertDialog.Builder chooser = new AlertDialog.Builder(this);
        chooser.setTitle("📂 Geri Yüklenecek Yedeği Seçin (" + sortedFiles.length + " dosya)");
        chooser.setItems(displayItems, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                File selected = sortedFiles[which];
                restoreFromFile(selected);
            }
        });
        chooser.setNegativeButton("İptal", null);
        chooser.show();
    }

    private void restoreFromFile(File file) {
        try {
            String jsonStr = readFileToString(file);
            WorkspaceConfig imported = WorkspaceStorage.importFromJsonString(jsonStr);
            if (imported != null) {
                config = imported;
                WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                workspaceView.setConfig(config, iconCache);
                setupTileClickListeners();
                Toast.makeText(MainActivity.this, "✅ " + file.getName() + "\nbaşarıyla geri yüklendi!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this, "❌ Geçersiz yedek dosyası içeriği!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "Yedek yüklenemedi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String readFileToString(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        fis.close();
        return sb.toString();
    }

    private void openRawJsonDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📋 JSON Metin ile Yedekleme / Düzenleme");

        final EditText input = new EditText(this);
        input.setText(WorkspaceStorage.exportToJsonString(config));
        input.setSelectAllOnFocus(true);
        input.setTextSize(11);
        builder.setView(input);

        builder.setPositiveButton("Geri Yükle", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String jsonStr = input.getText().toString();
                WorkspaceConfig imported = WorkspaceStorage.importFromJsonString(jsonStr);
                if (imported != null) {
                    config = imported;
                    WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                    workspaceView.setConfig(config, iconCache);
                    setupTileClickListeners();
                    Toast.makeText(MainActivity.this, "Yedek başarıyla geri yüklendi!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Geçersiz JSON verisi!", Toast.LENGTH_LONG).show();
                }
            }
        });

        builder.setNegativeButton("Kapat", null);
        builder.show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TV Kumandası 4 Renk Tuşları (Kırmızı, Yeşil, Sarı, Mavi)
        if (event.getRepeatCount() == 0) {
            if (keyCode == KeyEvent.KEYCODE_PROG_RED) {
                triggerColorKey(TileType.COLOR_RED);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_PROG_GREEN) {
                triggerColorKey(TileType.COLOR_GREEN);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_PROG_YELLOW) {
                triggerColorKey(TileType.COLOR_YELLOW);
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_PROG_BLUE) {
                triggerColorKey(TileType.COLOR_BLUE);
                return true;
            }
        }

        if (selectedMovingTile != null) {
            return handleTileMovement(keyCode);
        } else {
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                openLauncherSettingsDialog();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                handleDpadNavigation(keyCode);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                event.startTracking();
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private boolean isLongPressHandled = false;

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            isLongPressHandled = true;
            if (workspaceView != null) {
                workspaceView.executeTvSelectedLongClick();
            }
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (isLongPressHandled || (SystemClock.uptimeMillis() - lastLongClickTimestamp < 800)) {
                isLongPressHandled = false;
                return true;
            }
            if (workspaceView != null) {
                workspaceView.executeTvSelected();
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    // ==========================================
    // SANAL TV KUMANDASI TEST PANELİ (VIRTUAL REMOTE)
    // ==========================================

    private void sendKeyEvent(int keyCode) {
        // D-Pad Yön ve Seçim Tuşları için TV Focus Motoru
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER) {
            handleDpadNavigation(keyCode);
            return;
        }

        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
        dispatchKeyEvent(down);
        dispatchKeyEvent(up);
    }

    private boolean handleTileMovement(int keyCode) {
        if (selectedMovingTile == null) return false;

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            selectedMovingTile = null;
            Toast.makeText(this, "Taşıma işlemi iptal edildi.", Toast.LENGTH_SHORT).show();
            return true;
        }

        int cols = workspaceView.getCalculatedCols();
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            moveTileInStream(selectedMovingTile, -cols);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            moveTileInStream(selectedMovingTile, cols);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            moveTileInStream(selectedMovingTile, -1);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            moveTileInStream(selectedMovingTile, 1);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            selectedMovingTile = null;
            Toast.makeText(this, "Kutu konumu bırakıldı.", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private void handleDpadNavigation(int keyCode) {
        if (workspaceView == null || workspaceView.getChildCount() == 0) return;

        // Kutu taşınıyorsa
        if (selectedMovingTile != null) {
            handleTileMovement(keyCode);
            return;
        }

        // [OK] Tuşu: Odaktaki kutuyu başlat
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (SystemClock.uptimeMillis() - lastLongClickTimestamp < 800) {
                return;
            }
            workspaceView.executeTvSelected();
            return;
        }

        // Yön tuşları: 2D Uzaysal TV Gezintisi
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            workspaceView.navigateTv(keyCode);
            updateRemoteTitleWithSelected();
        }
    }

    private void updateRemoteTitleWithSelected() {
        if (remoteTitleView != null && workspaceView != null) {
            TileView sel = workspaceView.getTvSelectedTile();
            if (sel == null) {
                View focused = workspaceView.findFocus();
                if (focused instanceof TileView) {
                    sel = (TileView) focused;
                }
            }
            if (sel != null && sel.getTile() != null && sel.getTile().label != null) {
                if (sel.getTile().type == TileType.SPACER) {
                    remoteTitleView.setText("🎮 🔲 " + sel.getTile().colSpan + "x" + sel.getTile().rowSpan + " Boşluk");
                } else {
                    remoteTitleView.setText("🎮 " + sel.getTile().label);
                }
            } else {
                remoteTitleView.setText("🎮 TV Kumandası (Sürükle)");
            }
        }
    }

    private void updateVirtualRemoteOverlay() {
        if (rootContainer == null) return;

        if (virtualRemoteView != null) {
            rootContainer.removeView(virtualRemoteView);
            virtualRemoteView = null;
            remoteTitleView = null;
        }

        if (config == null || !config.virtualRemoteEnabled) {
            if (workspaceView != null) {
                workspaceView.clearTvSelection();
            }
            return;
        }

        int pad = dpToPx(10);
        int btnPad = dpToPx(6);

        final LinearLayout remoteLayout = new LinearLayout(this);
        remoteLayout.setOrientation(LinearLayout.VERTICAL);
        remoteLayout.setPadding(pad, pad, pad, pad);
        remoteLayout.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E618181A"));
        bg.setCornerRadius(dpToPx(14));
        bg.setStroke(dpToPx(1), Color.parseColor("#4DFFFFFF"));
        remoteLayout.setBackground(bg);

        // 1. Üst Başlık & Kapatma & SÜRÜKLEME TUTAMACI
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, dpToPx(6));

        TextView title = new TextView(this);
        title.setText("🎮 TV Kumandası (Sürükle)");
        title.setTextColor(Color.parseColor("#80D8FF"));
        title.setTextSize(11);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(title);
        remoteTitleView = title;

        TextView closeBtn = new TextView(this);
        closeBtn.setText(" ✕ ");
        closeBtn.setTextColor(Color.parseColor("#FF5252"));
        closeBtn.setTextSize(13);
        closeBtn.setPadding(btnPad, 0, btnPad, 0);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                config.virtualRemoteEnabled = false;
                WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                updateVirtualRemoteOverlay();
                Toast.makeText(MainActivity.this, "Sanal kumanda kapatıldı.", Toast.LENGTH_SHORT).show();
            }
        });
        headerRow.addView(closeBtn);
        remoteLayout.addView(headerRow);

        // Sürüklenebilir Panel (Draggable Overlay)
        headerRow.setOnTouchListener(new View.OnTouchListener() {
            private float initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        initialX = remoteLayout.getTranslationX();
                        initialY = remoteLayout.getTranslationY();
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        remoteLayout.setTranslationX(initialX + dx);
                        remoteLayout.setTranslationY(initialY + dy);
                        return true;
                }
                return false;
            }
        });

        // 2. D-PAD
        LinearLayout rowUp = new LinearLayout(this);
        rowUp.setGravity(Gravity.CENTER);
        rowUp.addView(createRemoteButton("▲", KeyEvent.KEYCODE_DPAD_UP));
        remoteLayout.addView(rowUp);

        LinearLayout rowMid = new LinearLayout(this);
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
                if (workspaceView != null) {
                    workspaceView.executeTvSelectedLongClick();
                }
                return true;
            }
        });
        rowMid.addView(okBtn);

        rowMid.addView(createRemoteButton("▶", KeyEvent.KEYCODE_DPAD_RIGHT));
        remoteLayout.addView(rowMid);

        LinearLayout rowDown = new LinearLayout(this);
        rowDown.setGravity(Gravity.CENTER);
        rowDown.addView(createRemoteButton("▼", KeyEvent.KEYCODE_DPAD_DOWN));
        remoteLayout.addView(rowDown);

        // 3. Renk Tuşları
        LinearLayout rowColors = new LinearLayout(this);
        rowColors.setGravity(Gravity.CENTER);
        rowColors.setPadding(0, dpToPx(6), 0, dpToPx(4));
        rowColors.addView(createColorRemoteButton("🔴", KeyEvent.KEYCODE_PROG_RED));
        rowColors.addView(createColorRemoteButton("🟢", KeyEvent.KEYCODE_PROG_GREEN));
        rowColors.addView(createColorRemoteButton("🟡", KeyEvent.KEYCODE_PROG_YELLOW));
        rowColors.addView(createColorRemoteButton("🔵", KeyEvent.KEYCODE_PROG_BLUE));
        remoteLayout.addView(rowColors);

        // 4. Fonksiyon Tuşları
        LinearLayout rowActions = new LinearLayout(this);
        rowActions.setGravity(Gravity.CENTER);
        rowActions.addView(createActionButton("MENÜ", KeyEvent.KEYCODE_MENU));
        rowActions.addView(createActionButton("GERİ", KeyEvent.KEYCODE_BACK));
        remoteLayout.addView(rowActions);

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END
        );
        flp.setMargins(0, 0, dpToPx(16), dpToPx(16));
        virtualRemoteView = remoteLayout;
        rootContainer.addView(virtualRemoteView, flp);

        // İlk açılışta ekrandaki ilk kutuyu TV imleciyle seç ve başlığı güncelle
        workspaceView.selectFirstVisibleTile();
        updateRemoteTitleWithSelected();
    }

    private Button createRemoteButton(String text, final int keyCode) {
        final Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12);
        btn.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        btn.setFocusable(false);
        int size = dpToPx(38);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
        btn.setLayoutParams(lp);
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
                sendKeyEvent(keyCode);
            }
        });
        return btn;
    }

    private Button createColorRemoteButton(String text, final int keyCode) {
        final Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(12);
        btn.setBackgroundColor(Color.parseColor("#26FFFFFF"));
        btn.setFocusable(false);
        int size = dpToPx(34);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btn.setAlpha(0.4f);
                btn.animate().alpha(1.0f).setDuration(120).start();
                sendKeyEvent(keyCode);
            }
        });
        return btn;
    }

    private Button createActionButton(String text, final int keyCode) {
        final Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(10);
        btn.setBackgroundColor(Color.parseColor("#26FFFFFF"));
        btn.setFocusable(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(64), dpToPx(32));
        lp.setMargins(dpToPx(3), dpToPx(4), dpToPx(3), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btn.setAlpha(0.4f);
                btn.animate().alpha(1.0f).setDuration(120).start();
                sendKeyEvent(keyCode);
            }
        });
        return btn;
    }

    private int dpToPx(int dp) {
        return UiUtils.dpToPx(this, dp);
    }

    private int dpToPx(float dp) {
        return UiUtils.dpToPx(this, dp);
    }
}
