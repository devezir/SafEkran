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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.safekran.launcher.data.AppInfo;
import com.safekran.launcher.data.AppScanner;
import com.safekran.launcher.data.WorkspaceStorage;
import com.safekran.launcher.model.SafeArea;
import com.safekran.launcher.model.Tile;
import com.safekran.launcher.model.TileType;
import com.safekran.launcher.model.WorkspaceConfig;
import com.safekran.launcher.ui.TileView;
import com.safekran.launcher.ui.WorkspaceView;

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
            // Düzenleme modunda tıklama: Kutuyu taşımak için seç/bırak
            if (selectedMovingTile == null) {
                selectedMovingTile = tile;
                Toast.makeText(this, tile.label + " seçildi. Yön tuşlarıyla taşıyın, [OK] ile bırakın.", Toast.LENGTH_SHORT).show();
            } else {
                selectedMovingTile = null;
                WorkspaceStorage.saveWorkspace(this, config);
                Toast.makeText(this, "Kutu konumu kaydedildi.", Toast.LENGTH_SHORT).show();
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
                // Normal modda zaten görünmez ve tıklanamaz
                break;
        }
    }

    private void onTileLongClicked(final Tile tile) {
        // Uzun basma: Kutu seçenekleri (Boyut değiştir, Araya Boşluk Ekle, Sil)
        String[] options = new String[]{
                "Kutuyu Büyüt (+%5)",
                "Kutuyu Küçült (-%5)",
                "Sağına Boşluk (Spacer) Ekle",
                "Kutuyu Sil"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(tile.label + " — İşlemler");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) { // Büyüt
                    tile.widthPercent = Math.min(0.50f, tile.widthPercent + 0.05f);
                    tile.heightPercent = Math.min(0.50f, tile.heightPercent + 0.05f);
                } else if (which == 1) { // Küçült
                    tile.widthPercent = Math.max(0.05f, tile.widthPercent - 0.05f);
                    tile.heightPercent = Math.max(0.05f, tile.heightPercent - 0.05f);
                } else if (which == 2) { // Boşluk ekle
                    Tile spacer = new Tile(TileType.SPACER, "Boşluk", tile.xPercent + tile.widthPercent + config.gutterPercent, tile.yPercent, config.defaultTileWidth, config.defaultTileHeight);
                    config.tiles.add(spacer);
                } else if (which == 3) { // Sil
                    config.tiles.remove(tile);
                }
                WorkspaceStorage.saveWorkspace(MainActivity.this, config);
                workspaceView.rebuildViews();
                setupTileClickListeners();
            }
        });
        builder.show();
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

    private void openLauncherSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.app_signature_title));

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);

        TextView authorText = new TextView(this);
        authorText.setText(getString(R.string.app_signature_dev) + " | " + getString(R.string.app_version_label));
        authorText.setTextColor(Color.WHITE);
        authorText.setTextSize(14);
        layout.addView(authorText);

        addSafeMarginSlider(layout, "Üst Güvenli Alan (%):", config.safeArea.top, new MarginCallback() {
            @Override
            public void onMarginChanged(float val) {
                config.safeArea.top = val;
                workspaceView.requestLayout();
            }
        });
        addSafeMarginSlider(layout, "Alt Güvenli Alan (%):", config.safeArea.bottom, new MarginCallback() {
            @Override
            public void onMarginChanged(float val) {
                config.safeArea.bottom = val;
                workspaceView.requestLayout();
            }
        });
        addSafeMarginSlider(layout, "Sol Güvenli Alan (%):", config.safeArea.left, new MarginCallback() {
            @Override
            public void onMarginChanged(float val) {
                config.safeArea.left = val;
                workspaceView.requestLayout();
            }
        });
        addSafeMarginSlider(layout, "Sağ Güvenli Alan (%):", config.safeArea.right, new MarginCallback() {
            @Override
            public void onMarginChanged(float val) {
                config.safeArea.right = val;
                workspaceView.requestLayout();
            }
        });

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

        builder.setNegativeButton("Varsayılana Sıfırla", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                config = WorkspaceStorage.generateDefaultWorkspace(MainActivity.this);
                workspaceView.setConfig(config, iconCache, false);
                setupTileClickListeners();
                Toast.makeText(MainActivity.this, "Varsayılan ızgara yüklendi.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.show();
    }

    private interface MarginCallback {
        void onMarginChanged(float val);
    }

    private void addSafeMarginSlider(LinearLayout layout, String label, float initialVal, final MarginCallback callback) {
        final TextView labelView = new TextView(this);
        int percent = (int) (initialVal * 100);
        labelView.setText(label + " %" + percent);
        labelView.setTextColor(Color.parseColor("#CCCCCC"));
        labelView.setPadding(0, 16, 0, 4);
        layout.addView(labelView);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(20); // %0 ile %20 arası
        seekBar.setProgress(percent);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                labelView.setText(labelView.getText().toString().split(":")[0] + ": %" + progress);
                callback.onMarginChanged(progress / 100f);
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

            // Eğer bir kutu taşınmak için seçildiyse D-Pad tuşlarıyla %1 kaydır
            if (selectedMovingTile != null) {
                float step = 0.01f;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    selectedMovingTile.yPercent = Math.max(0.0f, selectedMovingTile.yPercent - step);
                    workspaceView.requestLayout();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    selectedMovingTile.yPercent = Math.min(1.0f - selectedMovingTile.heightPercent, selectedMovingTile.yPercent + step);
                    workspaceView.requestLayout();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    selectedMovingTile.xPercent = Math.max(0.0f, selectedMovingTile.xPercent - step);
                    workspaceView.requestLayout();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    selectedMovingTile.xPercent = Math.min(1.0f - selectedMovingTile.widthPercent, selectedMovingTile.xPercent + step);
                    workspaceView.requestLayout();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    selectedMovingTile = null;
                    WorkspaceStorage.saveWorkspace(this, config);
                    Toast.makeText(this, "Kutu konumu bırakıldı ve kaydedildi.", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }
        } else {
            // Normal modda kumandanın MENU tuşuna basılırsa doğrudan düzenleme moduna geç!
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                toggleEditMode();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // Ana ekranda geri tuşuna basıldığında başlatıcı kapanmaz
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }
}
