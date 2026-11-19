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
            return WorkspaceConfig.fromJson(root);
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
        config.safeArea = new SafeArea(0.03f, 0.03f, 0.03f, 0.03f); // %3 Optimum güvenli alan
        config.gutterPercent = 0.01f;     // %1 Gutter
        config.defaultTileWidth = 0.10f;   // %10 Seçenek B: Serbest Yüzde
        config.defaultTileHeight = 0.10f;  // %10 Seçenek B: Serbest Yüzde

        // 1. Üst Satır Aksiyon Kutucukları
        // Saat ve Tarih Kutusu (Genişlik: %21, Yükseklik: %10)
        Tile clockTile = new Tile(TileType.CLOCK, "Saat & Tarih", 0.0f, 0.0f, 0.21f, 0.10f);
        config.tiles.add(clockTile);

        // Düzenleme Modu Kutusu
        Tile editTile = new Tile(TileType.EDIT_MODE, "Düzenle", 0.22f, 0.0f, 0.10f, 0.10f);
        config.tiles.add(editTile);

        // Başlatıcı Ayarları Kutusu
        Tile settingsTile = new Tile(TileType.LAUNCHER_SETTINGS, "Başlatıcı Ayarları", 0.33f, 0.0f, 0.10f, 0.10f);
        config.tiles.add(settingsTile);

        // Android Sistem Ayarları Kutusu
        Tile sysSettingsTile = new Tile(TileType.SYSTEM_SETTINGS, "Sistem Ayarları", 0.44f, 0.0f, 0.10f, 0.10f);
        config.tiles.add(sysSettingsTile);

        // 2. Yüklü Uygulamaları Sırala (İlk Açılış Otomatik Düzeni)
        List<AppInfo> apps = AppScanner.scanAllApps(context);
        float currentX = 0.0f;
        float currentY = 0.12f; // Aksiyon çubuğunun altından başla
        float tileW = config.defaultTileWidth;
        float tileH = config.defaultTileHeight;
        float gutter = config.gutterPercent;

        for (AppInfo app : apps) {
            if (currentX + tileW > 1.001f) {
                // Satır taştı, bir alt satıra geç
                currentX = 0.0f;
                currentY += tileH + gutter;
            }

            Tile appTile = new Tile(TileType.APP, app.label, currentX, currentY, tileW, tileH);
            appTile.packageName = app.packageName;
            appTile.activityName = app.activityName;
            config.tiles.add(appTile);

            currentX += tileW + gutter;
        }

        saveWorkspace(context, config);
        return config;
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
