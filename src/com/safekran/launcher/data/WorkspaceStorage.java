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

            // Sürüm 2 Göçü (Üst satırdaki boşluğu giderip her şeyi kesintisiz akışa bağlama)
            if (config.schemaVersion < 2) {
                List<AppInfo> apps = AppScanner.scanAllApps(context);
                reflowContinuousTiles(config, apps);
                config.schemaVersion = 2;
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
        config.schemaVersion = 2;
        config.safeArea = new SafeArea(0.03f, 0.03f, 0.03f, 0.03f); // %3 Optimum güvenli alan
        config.gutterPercent = 0.01f;     // %1 Gutter
        config.defaultTileWidth = 0.10f;   // %10 Seçenek B: Serbest Yüzde
        config.defaultTileHeight = 0.10f;  // %10 Seçenek B: Serbest Yüzde
        config.focusScale = 1.06f;
        config.themeMode = "BLACK";

        List<AppInfo> apps = AppScanner.scanAllApps(context);
        reflowContinuousTiles(config, apps);

        saveWorkspace(context, config);
        return config;
    }

    /**
     * Tüm kutucukları (Saat, Düzenle, Ayarlar, Sistem Ayarları ve tüm Uygulamaları)
     * en üst satırın sağında hiçbir boşluk bırakmadan, satır satır kesintisiz bir akışla dizer.
     */
    public static void reflowContinuousTiles(WorkspaceConfig config, List<AppInfo> apps) {
        config.tiles.clear();

        float tileW = config.defaultTileWidth;
        float tileH = config.defaultTileHeight;
        float gutter = config.gutterPercent;

        float currentX = 0.0f;
        float currentY = 0.0f;

        // 1. Saat ve Tarih Kutusu (2 birim genişlik: tileW * 2 + gutter)
        float clockW = (tileW * 2f) + gutter;
        Tile clockTile = new Tile(TileType.CLOCK, "Saat & Tarih", currentX, currentY, clockW, tileH);
        config.tiles.add(clockTile);
        currentX += clockW + gutter;

        // 2. Düzenleme Modu Kutusu
        Tile editTile = new Tile(TileType.EDIT_MODE, "Düzenle", currentX, currentY, tileW, tileH);
        config.tiles.add(editTile);
        currentX += tileW + gutter;

        // 3. Başlatıcı Ayarları Kutusu
        Tile settingsTile = new Tile(TileType.LAUNCHER_SETTINGS, "Başlatıcı Ayarları", currentX, currentY, tileW, tileH);
        config.tiles.add(settingsTile);
        currentX += tileW + gutter;

        // 4. Sistem Ayarları Kutusu
        Tile sysSettingsTile = new Tile(TileType.SYSTEM_SETTINGS, "Sistem Ayarları", currentX, currentY, tileW, tileH);
        config.tiles.add(sysSettingsTile);
        currentX += tileW + gutter;

        // 5. Yüklü Uygulamalar — HİÇ BOŞLUK BIRAKMADAN AYNI SATIRDAN DEVAM EDER!
        for (AppInfo app : apps) {
            // Eğer sıradaki kutucuk bu satıra sığmıyorsa alt satıra geç
            if (currentX + tileW > 1.001f) {
                currentX = 0.0f;
                currentY += tileH + gutter;
            }

            Tile appTile = new Tile(TileType.APP, app.label, currentX, currentY, tileW, tileH);
            appTile.packageName = app.packageName;
            appTile.activityName = app.activityName;
            config.tiles.add(appTile);

            currentX += tileW + gutter;
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
