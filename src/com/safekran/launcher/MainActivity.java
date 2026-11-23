package com.safekran.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.safekran.launcher.data.AppInfo;
import com.safekran.launcher.data.AppScanner;
import com.safekran.launcher.data.BackupRestoreManager;
import com.safekran.launcher.data.WorkspaceStorage;
import com.safekran.launcher.model.Tile;
import com.safekran.launcher.model.TileType;
import com.safekran.launcher.model.WorkspaceConfig;
import com.safekran.launcher.ui.LauncherSettingsDialog;
import com.safekran.launcher.ui.TileContextMenuHelper;
import com.safekran.launcher.ui.TileView;
import com.safekran.launcher.ui.VirtualRemoteOverlay;
import com.safekran.launcher.ui.WorkspaceView;
import com.safekran.launcher.util.IntentUtils;
import com.safekran.launcher.util.UiUtils;
import com.safekran.launcher.util.UpdateFlowManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Saf Ekran - Ana Başlatıcı Aktivitesi (Main Launcher Activity).
 * Mimari: Modüler, saf SDK (no-dependency), TV/Mobil uyumlu Windows Phone esintili başlatıcı.
 */
public class MainActivity extends Activity {

    private WorkspaceView workspaceView;
    private WorkspaceConfig config;
    private FrameLayout rootContainer;
    private VirtualRemoteOverlay virtualRemoteOverlay = null;
    private final Map<String, Drawable> iconCache = new HashMap<String, Drawable>();

    // Düzenleme modunda taşınan kutucuk durumu
    private Tile selectedMovingTile = null;

    // Canlı Pil Dinleyicisi (Ekran açıkken anlık güncelleme)
    private BroadcastReceiver batteryReceiver = null;

    // Uzun basma sonrası artçı tıklamaları yutan emniyet damgası (Debounce Guard)
    private long lastLongClickTimestamp = 0;

    // Çift tıklama ve kuyruk yığılmasını önleyen emniyet damgası
    private long lastTileClickTimestamp = 0;

    private boolean isLongPressHandled = false;

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
        rootContainer.addView(workspaceView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
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
    public void onConfigurationChanged(Configuration newConfig) {
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
        UpdateFlowManager.checkPendingUpdateOnResume(this, (config != null) ? config.settingsUiScale : 1.0f);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterBatteryReceiver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LauncherSettingsDialog.dismissCurrent();
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
                UiUtils.showToast(this, selectedMovingTile.label + " ile " + tile.label + " yer değiştirdi.");
            }
            finishTileMove(null);
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
                IntentUtils.openSystemSettings(this);
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
            case BATTERY:
            case RAM:
            case STORAGE:
            case NETWORK:
            case BLUETOOTH:
                IntentUtils.openStatusTileSettings(this, tile.type);
                break;
            case SPACER:
                onTileLongClicked(tile);
                break;
        }
    }

    private final TileContextMenuHelper.TileContextMenuListener contextMenuListener = new TileContextMenuHelper.TileContextMenuListener() {
        @Override
        public void onSaveAndRefresh(String message) {
            saveAndRefresh(message);
        }

        @Override
        public void onStartTileMove(Tile t) {
            startTileMove(t);
        }

        @Override
        public void onOpenClockApp() {
            IntentUtils.openClockApp(MainActivity.this);
        }

        @Override
        public void onOpenLauncherSettings() {
            openLauncherSettingsDialog();
        }
    };

    private void onTileLongClicked(final Tile tile) {
        lastLongClickTimestamp = SystemClock.uptimeMillis();
        TileContextMenuHelper.openTileContextMenu(this, config, iconCache, tile,
                workspaceView.getCalculatedCols(), contextMenuListener);
    }

    private void startTileMove(final Tile tile) {
        selectedMovingTile = tile;
        workspaceView.post(new Runnable() {
            @Override
            public void run() {
                TileView tv = workspaceView.findTileView(tile);
                if (tv != null) {
                    workspaceView.setTvSelectedTile(tv);
                    workspaceView.ensureTileVisible(tv);
                }
                updateRemoteTitleWithSelected();
            }
        });
        UiUtils.showToastLong(MainActivity.this, tile.label + " seçildi. Kumanda yön tuşlarıyla veya hedef kutuya dokunarak yerini değiştirin. Bırakmak için [OK] tuşuna basın.");
    }

    private void saveAndRefresh(String toastMsg) {
        WorkspaceStorage.saveWorkspace(MainActivity.this, config);
        workspaceView.rebuildViews();
        setupTileClickListeners();
        if (toastMsg != null && !toastMsg.isEmpty()) {
            UiUtils.showToast(MainActivity.this, toastMsg);
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
                UiUtils.showToast(this, tile.label + " için henüz bir uygulama atanmamış. Kutuya uzun basarak atayabilirsiniz.");
            }
        } else if ("HIDDEN_APPS".equals(action)) {
            openHiddenAppsDialog(null);
        } else if ("THEME_TOGGLE".equals(action)) {
            toggleThemeQuickly();
        } else if ("LAUNCHER_SETTINGS".equals(action)) {
            openLauncherSettingsDialog();
        } else if ("SYSTEM_SETTINGS".equals(action)) {
            IntentUtils.openSystemSettings(this);
        } else {
            UiUtils.showToast(this, tile.label + " için işlem atanmamış. Kutuya uzun basarak işlem atayabilirsiniz.");
        }
    }

    private void toggleThemeQuickly() {
        if ("LIGHT".equals(config.themeMode)) {
            config.themeMode = "BLACK";
            UiUtils.showToast(this, "🌙 Saf Siyah Tema");
        } else {
            config.themeMode = "LIGHT";
            UiUtils.showToast(this, "☀️ Aydınlık Tema");
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
            String colorName = Tile.getColorName(colorType);
            UiUtils.showToast(this, colorName + " tuşuna ait kutu ana ekranda bulunmuyor.");
        }
    }

    private void openHiddenAppsDialog(final Tile anchorTile) {
        TileContextMenuHelper.openHiddenAppsDialog(this, config, iconCache, anchorTile, contextMenuListener);
    }

    private void swapTilePositions(Tile t1, Tile t2) {
        int idx1 = config.tiles.indexOf(t1);
        int idx2 = config.tiles.indexOf(t2);
        if (idx1 >= 0 && idx2 >= 0) {
            Collections.swap(config.tiles, idx1, idx2);
        }
    }

    private void moveTileInStream(final Tile tile, int delta) {
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

        workspaceView.post(new Runnable() {
            @Override
            public void run() {
                TileView tv = workspaceView.findTileView(tile);
                if (tv != null) {
                    workspaceView.setTvSelectedTile(tv);
                    workspaceView.ensureTileVisible(tv);
                }
                updateRemoteTitleWithSelected();
            }
        });
    }

    private void launchApp(Tile tile) {
        if (tile == null) return;
        launchApp(tile.packageName, tile.activityName);
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
                UiUtils.showToast(this, "Uygulama başlatılamadı: " + packageName);
            }
        } catch (Exception e) {
            UiUtils.showToast(this, "Hata: " + e.getMessage());
        }
    }

    private void openLauncherSettingsDialog() {
        LauncherSettingsDialog.show(this, config, workspaceView, iconCache, new LauncherSettingsDialog.LauncherSettingsListener() {
            @Override
            public void onRefreshViewsAndListeners() {
                WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                workspaceView.rebuildViews();
                setupTileClickListeners();
            }

            @Override
            public void onVirtualRemoteToggled(boolean enabled) {
                updateVirtualRemoteOverlay();
            }

            @Override
            public void onOpenHiddenApps() {
                openHiddenAppsDialog(null);
            }

            @Override
            public void onOpenBackupRestore() {
                BackupRestoreManager.openBackupRestoreDialog(MainActivity.this, config,
                        new BackupRestoreManager.OnConfigRestoredCallback() {
                            @Override
                            public void onConfigRestored(WorkspaceConfig newConfig) {
                                config = newConfig;
                                WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                                workspaceView.setConfig(config, iconCache);
                                setupTileClickListeners();
                            }
                        });
            }

            @Override
            public void onResetToFactory() {
                config = WorkspaceStorage.generateDefaultWorkspace(MainActivity.this);
                workspaceView.setConfig(config, iconCache);
                setupTileClickListeners();
                UiUtils.showToast(MainActivity.this, "Fabrika ayarlarına dönüldü.");
            }
        });
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

    private void finishTileMove(String toastMsg) {
        selectedMovingTile = null;
        updateRemoteTitleWithSelected();
        if (toastMsg != null) {
            UiUtils.showToast(this, toastMsg);
        }
    }

    private boolean handleTileMovement(int keyCode) {
        if (selectedMovingTile == null) return false;

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finishTileMove("Taşıma işlemi iptal edildi.");
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
            finishTileMove("Kutu konumu bırakıldı.");
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
        if (virtualRemoteOverlay == null || workspaceView == null) return;

        if (selectedMovingTile != null) {
            if (selectedMovingTile.type == TileType.SPACER) {
                virtualRemoteOverlay.updateTitle("📦 [TAŞINIYOR] " + selectedMovingTile.colSpan + "x" + selectedMovingTile.rowSpan + " Boşluk");
            } else {
                virtualRemoteOverlay.updateTitle("📦 [TAŞINIYOR] " + selectedMovingTile.label);
            }
            return;
        }

        TileView sel = workspaceView.getTvSelectedTile();
        if (sel == null) {
            View focused = workspaceView.findFocus();
            if (focused instanceof TileView) {
                sel = (TileView) focused;
            }
        }
        if (sel != null && sel.getTile() != null && sel.getTile().label != null) {
            if (sel.getTile().type == TileType.SPACER) {
                virtualRemoteOverlay.updateTitle("🎮 🔲 " + sel.getTile().colSpan + "x" + sel.getTile().rowSpan + " Boşluk");
            } else {
                virtualRemoteOverlay.updateTitle("🎮 " + sel.getTile().label);
            }
        } else {
            virtualRemoteOverlay.updateTitle("🎮 TV Kumandası (Sürükle)");
        }
    }

    private void updateVirtualRemoteOverlay() {
        if (rootContainer == null) return;

        if (virtualRemoteOverlay != null) {
            virtualRemoteOverlay.detachFrom(rootContainer);
            virtualRemoteOverlay = null;
        }

        if (config == null || !config.virtualRemoteEnabled) {
            if (workspaceView != null) {
                workspaceView.clearTvSelection();
            }
            return;
        }

        virtualRemoteOverlay = new VirtualRemoteOverlay(this, new VirtualRemoteOverlay.RemoteActionListener() {
            @Override
            public void onSendKeyEvent(int keyCode) {
                sendKeyEvent(keyCode);
            }

            @Override
            public void onExecuteLongClick() {
                lastLongClickTimestamp = SystemClock.uptimeMillis();
                if (workspaceView != null) {
                    workspaceView.executeTvSelectedLongClick();
                }
            }

            @Override
            public void onCloseRemote() {
                config.virtualRemoteEnabled = false;
                WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                updateVirtualRemoteOverlay();
                UiUtils.showToast(MainActivity.this, "Sanal kumanda kapatıldı.");
            }
        });

        virtualRemoteOverlay.attachTo(rootContainer);

        // İlk açılışta ekrandaki ilk kutuyu TV imleciyle seç ve başlığı güncelle
        workspaceView.selectFirstVisibleTile();
        updateRemoteTitleWithSelected();
    }
}
