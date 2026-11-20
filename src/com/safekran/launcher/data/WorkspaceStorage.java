package com.safekran.launcher.data;

import android.content.Context;
import com.safekran.launcher.model.SafeArea;
import com.safekran.launcher.model.Tile;
import com.safekran.launcher.model.TileType;
import com.safekran.launcher.model.WorkspaceConfig;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.List;

public class WorkspaceStorage {

    private static final String FILE_NAME = "workspace.json";

    public static WorkspaceConfig loadWorkspace(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            return generateDefaultWorkspace(context);
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            fis.close();

            JSONObject root = new JSONObject(sb.toString());
            WorkspaceConfig config = WorkspaceConfig.fromJson(root);

            // Sürüm 4 Göçü (Otomatik Türetilen Sütun ve Boyut Modeli)
            if (config.schemaVersion < 4) {
                List<AppInfo> apps = AppScanner.scanAllApps(context);
                reflowContinuousTiles(config, apps, 8);
                config.schemaVersion = 4;
                saveWorkspace(context, config);
            }

            // Düzenleme Modu Kaldırıldı: Eski EDIT_MODE tuşlarını güvenle boş/özelleştirilebilir renge dönüştür
            boolean migrated = false;
            for (Tile t : config.tiles) {
                if ("EDIT_MODE".equals(t.actionType)) {
                    t.actionType = "NONE";
                    if (t.label != null && t.label.contains("Düzenle")) {
                        t.label = "🔴 Kırmızı Tuş";
                    }
                    migrated = true;
                }
            }
            if (migrated) {
                saveWorkspace(context, config);
            }

            return config;
        } catch (Exception e) {
            e.printStackTrace();
            return generateDefaultWorkspace(context);
        }
    }

    public static boolean saveWorkspace(Context context, WorkspaceConfig config) {
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            FileOutputStream fos = new FileOutputStream(file);
            String jsonStr = config.toJson().toString(2);
            fos.write(jsonStr.getBytes("UTF-8"));
            fos.flush();
            fos.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static WorkspaceConfig generateDefaultWorkspace(Context context) {
        WorkspaceConfig config = new WorkspaceConfig();
        config.schemaVersion = 4;
        config.safeArea = new SafeArea(0.03f, 0.03f, 0.03f, 0.03f); // %3 Optimum güvenli alan
        config.tileScale = 0.10f;          // %10 Kutu Boyutu
        config.tileHeightRatio = 0.75f;     // %75 Yükseklik Oranı (16:9 TV için ideal)
        config.gutterPercent = 0.01f;      // %1 Gutter
        config.focusScale = 1.06f;
        config.themeMode = "BLACK";

        List<AppInfo> apps = AppScanner.scanAllApps(context);
        reflowContinuousTiles(config, apps, 8);

        saveWorkspace(context, config);
        return config;
    }

    /**
     * Tüm kutuları verilen sütun kapasitesine göre (cols)
     * en üst satır dahil sağ kenarda hiç boşluk bırakmadan, satır satır akıtır.
     */
    public static void reflowContinuousTiles(WorkspaceConfig config, List<AppInfo> apps, int cols) {
        config.tiles.clear();

        int maxCols = Math.max(2, cols);
        int currentCol = 0;
        int currentRow = 0;

        // 1. Saat Kutusu (2 Sütun genişlik, eğer 2'den az sütun varsa 1)
        int clockSpan = Math.min(2, maxCols);
        Tile clockTile = new Tile(TileType.CLOCK, "Saat & Tarih", currentCol, currentRow, clockSpan, 1);
        config.tiles.add(clockTile);
        currentCol += clockSpan;

        // 2. Kumanda 4 Renk Butonları (Kırmızı, Yeşil, Sarı, Mavi)
        if (currentCol >= maxCols) { currentCol = 0; currentRow++; }
        Tile redTile = new Tile(TileType.COLOR_RED, "🔴 Kırmızı Tuş", currentCol, currentRow, 1, 1);
        redTile.actionType = "NONE";
        config.tiles.add(redTile);
        currentCol += 1;

        if (currentCol >= maxCols) { currentCol = 0; currentRow++; }
        Tile greenTile = new Tile(TileType.COLOR_GREEN, "🟢 Gizlenenler", currentCol, currentRow, 1, 1);
        greenTile.actionType = "HIDDEN_APPS";
        config.tiles.add(greenTile);
        currentCol += 1;

        if (currentCol >= maxCols) { currentCol = 0; currentRow++; }
        Tile yellowTile = new Tile(TileType.COLOR_YELLOW, "🟡 Tema", currentCol, currentRow, 1, 1);
        yellowTile.actionType = "THEME_TOGGLE";
        config.tiles.add(yellowTile);
        currentCol += 1;

        if (currentCol >= maxCols) { currentCol = 0; currentRow++; }
        Tile blueTile = new Tile(TileType.COLOR_BLUE, "🔵 Ayarlar", currentCol, currentRow, 1, 1);
        blueTile.actionType = "LAUNCHER_SETTINGS";
        config.tiles.add(blueTile);
        currentCol += 1;

        // 3. Sistem Ayarları Kutusu
        if (currentCol >= maxCols) { currentCol = 0; currentRow++; }
        Tile sysSettingsTile = new Tile(TileType.SYSTEM_SETTINGS, "Sistem Ayarları", currentCol, currentRow, 1, 1);
        config.tiles.add(sysSettingsTile);
        currentCol += 1;

        // 4. Yüklü Uygulamalar (HİÇ KESİNTİ OLMADAN AYNI SATIRDAN DEVAM EDER)
        for (AppInfo app : apps) {
            if (currentCol >= maxCols) {
                currentCol = 0;
                currentRow++;
            }

            Tile appTile = new Tile(TileType.APP, app.label, currentCol, currentRow, 1, 1);
            appTile.packageName = app.packageName;
            appTile.activityName = app.activityName;
            config.tiles.add(appTile);

            currentCol += 1;
        }
    }

    public static String exportToJsonString(WorkspaceConfig config) {
        try {
            return config.toJson().toString(2);
        } catch (Exception e) {
            return "";
        }
    }

    public static WorkspaceConfig importFromJsonString(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            return WorkspaceConfig.fromJson(root);
        } catch (Exception e) {
            return null;
        }
    }
}
