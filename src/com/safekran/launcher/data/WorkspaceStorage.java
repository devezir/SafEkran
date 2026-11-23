package com.safekran.launcher.data;

import android.content.Context;
import com.safekran.launcher.model.SafeArea;
import com.safekran.launcher.model.Tile;
import com.safekran.launcher.model.TileType;
import com.safekran.launcher.model.WorkspaceConfig;

import org.json.JSONObject;

import java.io.File;
import java.util.List;

public class WorkspaceStorage {

    private static final String FILE_NAME = "workspace.json";

    public static WorkspaceConfig loadWorkspace(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            return generateDefaultWorkspace(context);
        }

        try {
            String jsonStr = com.safekran.launcher.util.IoUtils.readFileToString(file);
            JSONObject root = new JSONObject(jsonStr);
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

            // Sürüm 5 / Bağımsız DATE kutusu göçü (Varsayılan 2x Genişlik)
            boolean hasDate = false;
            int clockIndex = -1;
            for (int i = 0; i < config.tiles.size(); i++) {
                Tile t = config.tiles.get(i);
                if (t.type == TileType.CLOCK) {
                    clockIndex = i;
                    if (t.label != null && t.label.contains("Tarih")) {
                        t.label = "Saat";
                        migrated = true;
                    }
                } else if (t.type == TileType.DATE) {
                    hasDate = true;
                    if (t.colSpan == 1 && config.schemaVersion < 5) {
                        t.colSpan = 2;
                        migrated = true;
                    }
                }
            }
            if (!hasDate && clockIndex >= 0) {
                Tile dateTile = new Tile(TileType.DATE, "Tarih", 0, 0, 2, 1);
                config.tiles.add(clockIndex + 1, dateTile);
                migrated = true;
            }
            if (config.schemaVersion < 5) {
                config.schemaVersion = 5;
                migrated = true;
            }

            // Sürüm 7 / Başlatıcı Ayarları dahil tüm özel kutuların eksiksiz ana ekranda olması
            int insertIndex = -1;
            for (int i = 0; i < config.tiles.size(); i++) {
                if (config.tiles.get(i).type == TileType.SYSTEM_SETTINGS) {
                    insertIndex = i;
                    break;
                }
            }
            if (insertIndex < 0) insertIndex = Math.min(6, config.tiles.size());

            if (!config.hasTileType(TileType.BATTERY)) { config.tiles.add(insertIndex, new Tile(TileType.BATTERY, "Pil", 0, 0, 1, 1)); migrated = true; }
            if (!config.hasTileType(TileType.RAM)) { config.tiles.add(insertIndex, new Tile(TileType.RAM, "RAM", 0, 0, 1, 1)); migrated = true; }
            if (!config.hasTileType(TileType.STORAGE)) { config.tiles.add(insertIndex, new Tile(TileType.STORAGE, "Depolama", 0, 0, 1, 1)); migrated = true; }
            if (!config.hasTileType(TileType.NETWORK)) { config.tiles.add(insertIndex, new Tile(TileType.NETWORK, "Ağ", 0, 0, 1, 1)); migrated = true; }
            if (!config.hasTileType(TileType.BLUETOOTH)) { config.tiles.add(insertIndex, new Tile(TileType.BLUETOOTH, "Bluetooth", 0, 0, 1, 1)); migrated = true; }
            if (!config.hasTileType(TileType.LAUNCHER_SETTINGS)) { config.tiles.add(insertIndex, new Tile(TileType.LAUNCHER_SETTINGS, "Başlatıcı Ayarları", 0, 0, 1, 1)); migrated = true; }

            if (config.schemaVersion < 7) {
                config.schemaVersion = 7;
                migrated = true;
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
            com.safekran.launcher.util.IoUtils.writeStringToFile(file, config.toJson().toString(2));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static WorkspaceConfig generateDefaultWorkspace(Context context) {
        WorkspaceConfig config = new WorkspaceConfig();
        config.schemaVersion = 7;
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
        int[] cursor = new int[]{0, 0}; // [col, row]

        // 1. Saat Kutusu (2 Sütun genişlik, eğer 2'den az sütun varsa 1)
        int clockSpan = Math.min(2, maxCols);
        addTile(config, TileType.CLOCK, "Saat", clockSpan, 1, cursor, maxCols);

        // 2. Tarih Kutusu (Varsayılan 2 Sütun genişlik)
        int dateSpan = Math.min(2, maxCols);
        addTile(config, TileType.DATE, "Tarih", dateSpan, 1, cursor, maxCols);

        // 3. Kumanda 4 Renk Butonları (Kırmızı, Yeşil, Sarı, Mavi)
        addTile(config, TileType.COLOR_RED, "🔴 Kırmızı Tuş", 1, 1, cursor, maxCols).actionType = "NONE";
        addTile(config, TileType.COLOR_GREEN, "🟢 Gizlenenler", 1, 1, cursor, maxCols).actionType = "HIDDEN_APPS";
        addTile(config, TileType.COLOR_YELLOW, "🟡 Tema", 1, 1, cursor, maxCols).actionType = "THEME_TOGGLE";
        addTile(config, TileType.COLOR_BLUE, "🔵 Ayarlar", 1, 1, cursor, maxCols).actionType = "LAUNCHER_SETTINGS";

        // 4. Sistem Durum Kutuları (Pil, RAM, Depolama, Ağ, Bluetooth, Sistem Ayarları)
        addTile(config, TileType.BATTERY, "Pil", 1, 1, cursor, maxCols);
        addTile(config, TileType.RAM, "RAM", 1, 1, cursor, maxCols);
        addTile(config, TileType.STORAGE, "Depolama", 1, 1, cursor, maxCols);
        addTile(config, TileType.NETWORK, "Ağ", 1, 1, cursor, maxCols);
        addTile(config, TileType.BLUETOOTH, "Bluetooth", 1, 1, cursor, maxCols);
        addTile(config, TileType.LAUNCHER_SETTINGS, "Başlatıcı Ayarları", 1, 1, cursor, maxCols);
        addTile(config, TileType.SYSTEM_SETTINGS, "Sistem Ayarları", 1, 1, cursor, maxCols);

        // 5. Yüklü Uygulamalar (HİÇ KESİNTİ OLMADAN AYNI SATIRDAN DEVAM EDER)
        for (AppInfo app : apps) {
            Tile appTile = addTile(config, TileType.APP, app.label, 1, 1, cursor, maxCols);
            appTile.packageName = app.packageName;
            appTile.activityName = app.activityName;
        }
    }

    private static void placeTile(WorkspaceConfig config, Tile tile, int[] cursor, int maxCols) {
        if (cursor[0] + tile.colSpan > maxCols) {
            cursor[0] = 0;
            cursor[1]++;
        }
        tile.col = cursor[0];
        tile.row = cursor[1];
        config.tiles.add(tile);
        cursor[0] += tile.colSpan;
    }

    private static Tile addTile(WorkspaceConfig config, TileType type, String title, int colSpan, int rowSpan, int[] cursor, int maxCols) {
        Tile tile = new Tile(type, title, 0, 0, colSpan, rowSpan);
        placeTile(config, tile, cursor, maxCols);
        return tile;
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
