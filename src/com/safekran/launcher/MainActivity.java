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

    private void openHiddenAppsDialog(final Tile anchorTile) {
        final List<AppInfo> hidden = getHiddenOrMissingApps();
        if (hidden.isEmpty()) {
            Toast.makeText(this, "Gizlenmiş uygulama yok. Tüm uygulamalar ana ekranda mevcut.", Toast.LENGTH_SHORT).show();
            return;
        }

        final boolean hasMultiple = hidden.size() > 1;
        int listSize = hasMultiple ? hidden.size() + 1 : hidden.size();
        String[] items = new String[listSize];

        int offset = 0;
        if (hasMultiple) {
            items[0] = "➕ TÜMÜNÜ GERİ GETİR (" + hidden.size() + " Uygulama)";
            offset = 1;
        }

        for (int i = 0; i < hidden.size(); i++) {
            items[i + offset] = hidden.get(i).label;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(anchorTile != null ? anchorTile.label + " Yanına Uygulama Ekle" : "Gizlenen Uygulamaları Ekle (" + hidden.size() + ")");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (hasMultiple && which == 0) {
                    // Tümünü birden ekle
                    for (AppInfo ai : hidden) {
                        Tile nt = new Tile(TileType.APP, ai.label, 0, 0, 1, 1);
                        nt.packageName = ai.packageName;
                        iconCache.put(ai.packageName, ai.icon);
                        config.tiles.add(nt);
                    }
                    WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                    workspaceView.rebuildViews();
                    setupTileClickListeners();
                    Toast.makeText(MainActivity.this, hidden.size() + " uygulama ana ekrana eklendi.", Toast.LENGTH_SHORT).show();
                } else {
                    int selectedIndex = hasMultiple ? (which - 1) : which;
                    AppInfo selected = hidden.get(selectedIndex);
                    Tile newTile = new Tile(TileType.APP, selected.label, 0, 0, 1, 1);
                    newTile.packageName = selected.packageName;
                    iconCache.put(selected.packageName, selected.icon);

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

                    WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                    workspaceView.rebuildViews();
                    setupTileClickListeners();
                    Toast.makeText(MainActivity.this, selected.label + " ana ekrana eklendi.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.show();
    }

    private void onTileLongClicked(final Tile tile) {
        final List<AppInfo> hiddenApps = getHiddenOrMissingApps();
        String addAppOption = hiddenApps.isEmpty() ? "Sağına Uygulama Ekle (Gizlenen yok)" : "Sağına Uygulama Ekle (" + hiddenApps.size() + " gizlenen)";

        String[] options = new String[]{
                "Genişlet (+1 Sütun)",
                "Daralt (-1 Sütun)",
                "Yüksekliği Değiştir (1x/2x)",
                "Sağına Boşluk (Spacer) Ekle",
                addAppOption,
                "Kutuyu Kaldır (Gizle)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(tile.label + " — Kutu İşlemleri");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int maxC = workspaceView.getCalculatedCols();
                if (which == 0) {
                    tile.colSpan = Math.min(maxC, tile.colSpan + 1);
                    WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                    workspaceView.rebuildViews();
                    setupTileClickListeners();
                } else if (which == 1) {
                    tile.colSpan = Math.max(1, tile.colSpan - 1);
                    WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                    workspaceView.rebuildViews();
                    setupTileClickListeners();
                } else if (which == 2) {
                    tile.rowSpan = (tile.rowSpan == 1) ? 2 : 1;
                    WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                    workspaceView.rebuildViews();
                    setupTileClickListeners();
                } else if (which == 3) {
                    // Araya boşluk ekleme: Tam bu kutunun hemen arkasına Spacer ekle
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
                } else if (which == 4) {
                    if (hiddenApps.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Gizlenmiş uygulama yok. Tüm uygulamalarınız ekranda.", Toast.LENGTH_SHORT).show();
                    } else {
                        openHiddenAppsDialog(tile);
                    }
                } else if (which == 5) {
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
