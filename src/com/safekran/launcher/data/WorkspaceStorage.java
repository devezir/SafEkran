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

            // Sürüm 3 Göçü (Superpowers 2D Sanal Hücre Matrisi Mimarisi)
            if (config.schemaVersion < 3) {
                List<AppInfo> apps = AppScanner.scanAllApps(context);
                reflowContinuousTiles(config, apps);
                config.schemaVersion = 3;
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
        config.schemaVersion = 3;
        config.safeArea = new SafeArea(0.03f, 0.03f, 0.03f, 0.03f); // %3 Optimum güvenli alan
        config.colCount = 8;              // 8 Sütunlu TV ızgarası
        config.gutterPercent = 0.01f;     // %1 Gutter
        config.defaultTileHeight = 0.10f;  // %10 Yükseklik (TV 16:9 oranı)
        config.focusScale = 1.06f;
        config.themeMode = "BLACK";

        List<AppInfo> apps = AppScanner.scanAllApps(context);
        reflowContinuousTiles(config, apps);

        saveWorkspace(context, config);
        return config;
    }

    /**
     * Superpowers 2D Matris Düzenleyici:
     * Tüm kutuları hücre hücre (col, row, span) sıralar.
     * En üst satır dahil tüm satırlar boşluksuz, pürüzsüz doldurulur.
     */
    public static void reflowContinuousTiles(WorkspaceConfig config, List<AppInfo> apps) {
        config.tiles.clear();

        int maxCols = Math.max(4, config.colCount);
        int currentCol = 0;
        int currentRow = 0;

        // 1. Saat Kutusu (2 Sütun genişlik, 1 Satır yükseklik)
        int clockSpan = Math.min(2, maxCols);
        Tile clockTile = new Tile(TileType.CLOCK, "Saat & Tarih", currentCol, currentRow, clockSpan, 1);
        config.tiles.add(clockTile);
        currentCol += clockSpan;

        // 2. Düzenleme Modu Kutusu
        if (currentCol >= maxCols) { currentCol = 0; currentRow++; }
        Tile editTile = new Tile(TileType.EDIT_MODE, "Düzenle", currentCol, currentRow, 1, 1);
        config.tiles.add(editTile);
        currentCol += 1;

        // 3. Başlatıcı Ayarları Kutusu
        if (currentCol >= maxCols) { currentCol = 0; currentRow++; }
        Tile settingsTile = new Tile(TileType.LAUNCHER_SETTINGS, "Başlatıcı Ayarları", currentCol, currentRow, 1, 1);
        config.tiles.add(settingsTile);
        currentCol += 1;

        // 4. Sistem Ayarları Kutusu
        if (currentCol >= maxCols) { currentCol = 0; currentRow++; }
        Tile sysSettingsTile = new Tile(TileType.SYSTEM_SETTINGS, "Sistem Ayarları", currentCol, currentRow, 1, 1);
        config.tiles.add(sysSettingsTile);
        currentCol += 1;

        // 5. Yüklü Uygulamalar (HİÇ KESİNTİ OLMADAN AYNI SATIRDAN VE SONRAKİ SATIRLARDAN DEVAM EDER)
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
