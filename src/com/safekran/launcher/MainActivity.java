package com.safekran.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private WorkspaceView workspaceView;
    private WorkspaceConfig config;
    private final Map<String, Drawable> iconCache = new HashMap<String, Drawable>();

    // Düzenleme modunda taşınan kutucuk durumu
    private Tile selectedMovingTile = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Saf tam ekran & bildirim çubuğunu gizleme
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
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

        // Uygulama simgelerini önbelleğe al
        List<AppInfo> apps = AppScanner.scanAllApps(this);
        for (AppInfo ai : apps) {
            iconCache.put(ai.packageName, ai.icon);
        }

        // Yapılandırmayı yükle
        config = WorkspaceStorage.loadWorkspace(this);

        workspaceView = new WorkspaceView(this);
        workspaceView.setConfig(config, iconCache, false);
        setContentView(workspaceView);

        setupTileClickListeners();
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
                        onTileLongClicked(tile);
                        return true;
                    }
                });
            }
        }
    }

    private void onTileClicked(Tile tile, TileView tv) {
        if (workspaceView.isEditMode()) {
            if (selectedMovingTile == null) {
                selectedMovingTile = tile;
                Toast.makeText(this, tile.label + " seçildi. Yön tuşlarıyla veya diğer kutuya dokunarak yer değiştirin.", Toast.LENGTH_SHORT).show();
            } else {
                // Dokunmatik ile iki kutunun sırasını yer değiştir
                if (selectedMovingTile != tile) {
                    swapTilePositions(selectedMovingTile, tile);
                    Toast.makeText(this, selectedMovingTile.label + " ile " + tile.label + " yer değiştirdi.", Toast.LENGTH_SHORT).show();
                }
                selectedMovingTile = null;
                WorkspaceStorage.saveWorkspace(this, config);
                workspaceView.rebuildViews();
                setupTileClickListeners();
            }
            return;
        }

        switch (tile.type) {
            case APP:
                launchApp(tile.packageName);
                break;
            case EDIT_MODE:
                toggleEditMode();
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
                Toast.makeText(this, "Saat kutusu. Düzenle modunda taşınabilir.", Toast.LENGTH_SHORT).show();
                break;
            case SPACER:
                break;
        }
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
        if (config != null && config.tiles != null) {
            for (Tile t : config.tiles) {
                if (t.type == TileType.COLOR_RED) hasRed = true;
                else if (t.type == TileType.COLOR_GREEN) hasGreen = true;
                else if (t.type == TileType.COLOR_YELLOW) hasYellow = true;
                else if (t.type == TileType.COLOR_BLUE) hasBlue = true;
            }
        }
        if (!hasRed) {
            Tile r = new Tile(TileType.COLOR_RED, "🔴 Düzenle", 0, 0, 1, 1);
            r.actionType = "EDIT_MODE";
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
            items[idx++] = "[RENK KUTUSU] " + missingSpecials.get(i).label + " Kutusunu Ekle";
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
                launchApp(tile.packageName);
            } else {
                Toast.makeText(this, tile.label + " için henüz bir uygulama atanmamış. Kutuya uzun basarak atayabilirsiniz.", Toast.LENGTH_SHORT).show();
            }
        } else if ("EDIT_MODE".equals(action)) {
            toggleEditMode();
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
                "✏️ Düzenleme Modu Aç/Kapat",
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
                    tile.actionType = "EDIT_MODE";
                    tile.packageName = "";
                    tile.label = prefix + "Düzenle";
                    finishColorAssignment(tile);
                } else if (which == 2) {
                    tile.actionType = "HIDDEN_APPS";
                    tile.packageName = "";
                    tile.label = prefix + "Gizlenenler";
                    finishColorAssignment(tile);
                } else if (which == 3) {
                    tile.actionType = "THEME_TOGGLE";
                    tile.packageName = "";
                    tile.label = prefix + "Tema";
                    finishColorAssignment(tile);
                } else if (which == 4) {
                    tile.actionType = "LAUNCHER_SETTINGS";
                    tile.packageName = "";
                    tile.label = prefix + "Ayarlar";
                    finishColorAssignment(tile);
                } else if (which == 5) {
                    tile.actionType = "SYSTEM_SETTINGS";
                    tile.packageName = "";
                    tile.label = prefix + "Sistem";
                    finishColorAssignment(tile);
                } else if (which == 6) {
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
        final boolean isColorTile = (tile.type == TileType.COLOR_RED ||
                tile.type == TileType.COLOR_GREEN ||
                tile.type == TileType.COLOR_YELLOW ||
                tile.type == TileType.COLOR_BLUE);

        final List<AppInfo> hiddenApps = getHiddenOrMissingApps();
        String addAppOption = hiddenApps.isEmpty() ? "Sağına Uygulama Ekle (Gizlenen yok)" : "Sağına Uygulama Ekle (" + hiddenApps.size() + " gizlenen)";

        List<String> optionsList = new java.util.ArrayList<String>();
        if (isColorTile) {
            optionsList.add("🎯 İşlem / Uygulama Ata");
        }
        optionsList.add("Genişlet (+1 Sütun)");
        optionsList.add("Daralt (-1 Sütun)");
        optionsList.add("Yüksekliği Değiştir (1x/2x)");
        optionsList.add("Sağına Boşluk (Spacer) Ekle");
        optionsList.add(addAppOption);
        optionsList.add("Kutuyu Kaldır (Gizle)");

        String[] options = optionsList.toArray(new String[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(tile.label + " — Kutu İşlemleri");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int shift = isColorTile ? 1 : 0;

                if (isColorTile && which == 0) {
                    openColorAssignmentDialog(tile);
                    return;
                }

                int effectiveWhich = which - shift;
                int maxC = workspaceView.getCalculatedCols();
                if (effectiveWhich == 0) {
                    tile.colSpan = Math.min(maxC, tile.colSpan + 1);
                    WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                    workspaceView.rebuildViews();
                    setupTileClickListeners();
                } else if (effectiveWhich == 1) {
                    tile.colSpan = Math.max(1, tile.colSpan - 1);
                    WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                    workspaceView.rebuildViews();
                    setupTileClickListeners();
                } else if (effectiveWhich == 2) {
                    tile.rowSpan = (tile.rowSpan == 1) ? 2 : 1;
                    WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                    workspaceView.rebuildViews();
                    setupTileClickListeners();
                } else if (effectiveWhich == 3) {
                    int idx = config.tiles.indexOf(tile);
                    Tile spacer = new Tile(TileType.SPACER, "Boşluk", 0, 0, 1, 1);
                    if (idx >= 0 && idx < config.tiles.size()) {
                        config.tiles.add(idx + 1, spacer);
                    } else {
                        config.tiles.add(spacer);
                    }
                    WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                    workspaceView.rebuildViews();
                    setupTileClickListeners();
                } else if (effectiveWhich == 4) {
                    if (hiddenApps.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Gizlenmiş uygulama yok. Tüm uygulamalarınız ekranda.", Toast.LENGTH_SHORT).show();
                    } else {
                        openHiddenAppsDialog(tile);
                    }
                } else if (effectiveWhich == 5) {
                    config.tiles.remove(tile);
                    WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                    workspaceView.rebuildViews();
                    setupTileClickListeners();
                    Toast.makeText(MainActivity.this, tile.label + " gizlendi. Ayarlar veya kutu menüsünden geri getirebilirsiniz.", Toast.LENGTH_SHORT).show();
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

    private void launchApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
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

    private void toggleEditMode() {
        boolean newMode = !workspaceView.isEditMode();
        workspaceView.setEditMode(newMode);
        selectedMovingTile = null;
        setupTileClickListeners();
        String msg = newMode ? "Düzenleme Modu Açık. Çıkmak için [Geri] tuşuna basın." : "Düzenleme Modu Kapatıldı.";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void openSystemSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent altIntent = new Intent("android.settings.SETTINGS");
                altIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(altIntent);
            } catch (Exception ex) {
                Toast.makeText(this, "Sistem ayarları açılamadı.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==========================================
    // KAPSAMLI BAŞLATICI AYARLARI MENÜSÜ
    // ==========================================

    private void openLauncherSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Saf Ekran — Dinamik Izgara Ayarları");

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);

        // 1. İmza & Sürüm Başlığı
        TextView authorText = new TextView(this);
        authorText.setText("Geliştirici: Vezir | v2.2 (Kesintisiz Oto-Akış)");
        authorText.setTextColor(Color.parseColor("#4CAF50"));
        authorText.setTextSize(13);
        authorText.setPadding(0, 0, 0, 16);
        layout.addView(authorText);

        // 2. Güvenli Alan
        addSectionHeader(layout, "1. GÜVENLİ ALAN (OVERSCAN)");
        addPercentSlider(layout, "Üst Güvenli Alan", config.safeArea.top, 0, 20, new ValueCallback() {
            @Override public void onValueChanged(float val) { config.safeArea.top = val; workspaceView.requestLayout(); }
        });
        addPercentSlider(layout, "Alt Güvenli Alan", config.safeArea.bottom, 0, 20, new ValueCallback() {
            @Override public void onValueChanged(float val) { config.safeArea.bottom = val; workspaceView.requestLayout(); }
        });
        addPercentSlider(layout, "Sol Güvenli Alan", config.safeArea.left, 0, 20, new ValueCallback() {
            @Override public void onValueChanged(float val) { config.safeArea.left = val; workspaceView.requestLayout(); }
        });
        addPercentSlider(layout, "Sağ Güvenli Alan", config.safeArea.right, 0, 20, new ValueCallback() {
            @Override public void onValueChanged(float val) { config.safeArea.right = val; workspaceView.requestLayout(); }
        });

        // 3. Kutu Boyutları (Sütun Sayısı Artık Otomatik Çıkıyor!)
        addSectionHeader(layout, "2. KUTU GEOMETRİSİ (SÜTUN OTOMATİK HESAPLANIR)");
        addPercentSlider(layout, "Kutu Boyutu (Genişlik)", config.tileScale, 6, 20, new ValueCallback() {
            @Override public void onValueChanged(float val) { 
                config.tileScale = val; 
                workspaceView.requestLayout(); 
            }
        });
        addPercentSlider(layout, "Kutu Yükseklik Oranı (%75 = TV, %100 = Kare)", config.tileHeightRatio, 50, 100, new ValueCallback() {
            @Override public void onValueChanged(float val) { 
                config.tileHeightRatio = val; 
                workspaceView.requestLayout(); 
            }
        });
        addPercentSlider(layout, "Kutular Arası Boşluk (Gutter)", config.gutterPercent, 0, 5, new ValueCallback() {
            @Override public void onValueChanged(float val) { 
                config.gutterPercent = val; 
                workspaceView.requestLayout(); 
            }
        });

        // 4. Odaklanma Genişleme Değeri
        addSectionHeader(layout, "3. ODAK VE ETKİLEŞİM");
        float currentFocusPercent = (config.focusScale - 1.0f);
        addPercentSlider(layout, "Odaklanınca Büyüme Oranı", currentFocusPercent, 0, 15, new ValueCallback() {
            @Override public void onValueChanged(float val) { config.focusScale = 1.0f + val; }
        });

        // 5. Tema Seçimi
        addSectionHeader(layout, "4. TEMA MODU");
        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        addThemeButton(themeRow, "Saf Siyah", "BLACK");
        addThemeButton(themeRow, "Koyu Gri", "DARK_GRAY");
        addThemeButton(themeRow, "Aydınlık", "LIGHT");
        layout.addView(themeRow);

        // 6. Saat & Tarih Ayarları
        addSectionHeader(layout, "5. SAAT & TARİH");
        CheckBox cb24 = new CheckBox(this);
        cb24.setText("24 Saat Formatı (Örn: 22:30)");
        cb24.setTextColor(Color.WHITE);
        cb24.setChecked(config.is24Hour);
        cb24.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                config.is24Hour = isChecked;
            }
        });
        layout.addView(cb24);

        CheckBox cbDate = new CheckBox(this);
        cbDate.setText("Tarih Gösterilsin");
        cbDate.setTextColor(Color.WHITE);
        cbDate.setChecked(config.showDate);
        cbDate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                config.showDate = isChecked;
            }
        });
        layout.addView(cbDate);

        // 6. Gizlenen ve Eksik Uygulamalar
        addSectionHeader(layout, "6. GİZLENEN UYGULAMALAR");
        final List<AppInfo> currentHidden = getHiddenOrMissingApps();
        Button hiddenBtn = new Button(this);
        hiddenBtn.setText("👁️ Gizlenen Uygulamaları Yönet (" + currentHidden.size() + " adet)");
        hiddenBtn.setBackgroundColor(currentHidden.isEmpty() ? Color.parseColor("#37474F") : Color.parseColor("#00695C"));
        hiddenBtn.setTextColor(Color.WHITE);
        hiddenBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openHiddenAppsDialog(null);
            }
        });
        layout.addView(hiddenBtn);

        // 7. Düzen Sıfırlama Butonu
        addSectionHeader(layout, "7. DÜZEN İŞLEMLERİ");
        Button reflowBtn = new Button(this);
        reflowBtn.setText("🔄 Sıralamayı Varsayılana Sıfırla (Alfabeye Göre Sırala)");
        reflowBtn.setBackgroundColor(Color.parseColor("#37474F"));
        reflowBtn.setTextColor(Color.WHITE);
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
        layout.addView(reflowBtn);

        scroll.addView(layout);
        builder.setView(scroll);

        builder.setPositiveButton("Kaydet", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                workspaceView.rebuildViews();
                setupTileClickListeners();
                Toast.makeText(MainActivity.this, "Ayarlar kaydedildi.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNeutralButton("Yedekle / Geri Yükle", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                openBackupRestoreDialog();
            }
        });

        builder.setNegativeButton("Fabrika Ayarları", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                config = WorkspaceStorage.generateDefaultWorkspace(MainActivity.this);
                workspaceView.setConfig(config, iconCache, false);
                setupTileClickListeners();
                Toast.makeText(MainActivity.this, "Fabrika ayarlarına dönüldü.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.show();
    }

    private void addSectionHeader(LinearLayout layout, String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(Color.parseColor("#80D8FF"));
        header.setTextSize(12);
        header.setPadding(0, 20, 0, 6);
        layout.addView(header);
    }

    private void addThemeButton(LinearLayout row, String text, final String mode) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(11);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(4, 4, 4, 4);
        btn.setLayoutParams(params);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                config.themeMode = mode;
                workspaceView.rebuildViews();
                Toast.makeText(MainActivity.this, "Tema: " + mode, Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(btn);
    }

    private interface ValueCallback {
        void onValueChanged(float val);
    }

    private void addPercentSlider(LinearLayout layout, final String label, float initialVal, final int min, final int max, final ValueCallback callback) {
        final TextView labelView = new TextView(this);
        int percent = Math.round(initialVal * 100);
        labelView.setText(label + ": %" + percent);
        labelView.setTextColor(Color.parseColor("#CCCCCC"));
        labelView.setTextSize(12);
        labelView.setPadding(0, 10, 0, 2);
        layout.addView(labelView);

        SeekBar seekBar = new SeekBar(this);
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

    private void openBackupRestoreDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("JSON Yedekleme ve Geri Yükleme");

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
                    workspaceView.setConfig(config, iconCache, false);
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

        if (workspaceView.isEditMode()) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                toggleEditMode();
                return true;
            }

            // Superpowers Akış Hareketi: Kutu taşındığında sıralama listesinde dinamik yer değiştirir
            // Asla üst üste binme veya boşluk oluşamaz!
            if (selectedMovingTile != null) {
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
            }
        } else {
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                toggleEditMode();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }
}
