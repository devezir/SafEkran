package com.safekran.launcher.model;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/**
 * Superpowers 2D Sanal Hücre Matrisi Kutu Modeli (Cell Grid Tile).
 * Her kutu, ekrandaki tam hücre koordinatını (col, row) ve 
 * kapladığı hücre sayısını (colSpan, rowSpan) temsil eder.
 */
public class Tile {
    public String id;
    public TileType type;
    public String packageName;
    public String activityName;
    public String label;
    public String actionType; // "APP", "LAUNCHER_SETTINGS", "SYSTEM_SETTINGS", "THEME_TOGGLE", "HIDDEN_APPS", "NONE"

    // 2D Sanal Matris Hücre Koordinatları
    public int col;      // 0-tabanlı sütun indeksi (0..colCount-1)
    public int row;      // 0-tabanlı satır indeksi (0..N)
    public int colSpan;  // Yatayda kaç hücre kapladığı (Varsayılan 1)
    public int rowSpan;  // Dikeyde kaç hücre kapladığı (Varsayılan 1)

    public Tile() {
        this.id = UUID.randomUUID().toString();
        this.type = TileType.APP;
        this.colSpan = 1;
        this.rowSpan = 1;
    }

    public Tile(TileType type, String label, int col, int row, int colSpan, int rowSpan) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.label = label;
        this.col = col;
        this.row = row;
        this.colSpan = Math.max(1, colSpan);
        this.rowSpan = Math.max(1, rowSpan);
    }

    public boolean isColorTile() {
        return type == TileType.COLOR_RED || type == TileType.COLOR_GREEN ||
                type == TileType.COLOR_YELLOW || type == TileType.COLOR_BLUE;
    }

    public boolean isStatusTile() {
        return type == TileType.BATTERY || type == TileType.RAM ||
                type == TileType.STORAGE || type == TileType.NETWORK ||
                type == TileType.BLUETOOTH;
    }

    public String getColorName() {
        return getColorName(this.type);
    }

    public String getColorPrefix() {
        return getColorPrefix(this.type);
    }

    public static String getColorName(TileType t) {
        if (t == null) return "";
        switch (t) {
            case COLOR_RED: return "Kırmızı";
            case COLOR_GREEN: return "Yeşil";
            case COLOR_YELLOW: return "Sarı";
            case COLOR_BLUE: return "Mavi";
            default: return "";
        }
    }

    public static String getColorPrefix(TileType t) {
        if (t == null) return "";
        switch (t) {
            case COLOR_RED: return "🔴 ";
            case COLOR_GREEN: return "🟢 ";
            case COLOR_YELLOW: return "🟡 ";
            case COLOR_BLUE: return "🔵 ";
            default: return "";
        }
    }

    /**
     * Verilen TileType için standart boyut ve etiketli varsayılan kutu örneği üretir.
     */
    public static Tile createDefault(TileType type) {
        if (type == null) return null;
        switch (type) {
            case CLOCK:
                return new Tile(TileType.CLOCK, "Saat", 0, 0, 2, 1);
            case DATE:
                return new Tile(TileType.DATE, "Tarih", 0, 0, 2, 1);
            case BATTERY:
                return new Tile(TileType.BATTERY, "Pil", 0, 0, 1, 1);
            case RAM:
                return new Tile(TileType.RAM, "RAM", 0, 0, 1, 1);
            case STORAGE:
                return new Tile(TileType.STORAGE, "Depolama", 0, 0, 1, 1);
            case NETWORK:
                return new Tile(TileType.NETWORK, "Ağ", 0, 0, 1, 1);
            case BLUETOOTH:
                return new Tile(TileType.BLUETOOTH, "Bluetooth", 0, 0, 1, 1);
            case COLOR_RED: {
                Tile r = new Tile(TileType.COLOR_RED, "🔴 Kırmızı Tuş", 0, 0, 1, 1);
                r.actionType = "NONE";
                return r;
            }
            case COLOR_GREEN: {
                Tile g = new Tile(TileType.COLOR_GREEN, "🟢 Gizlenenler", 0, 0, 1, 1);
                g.actionType = "HIDDEN_APPS";
                return g;
            }
            case COLOR_YELLOW: {
                Tile y = new Tile(TileType.COLOR_YELLOW, "🟡 Tema", 0, 0, 1, 1);
                y.actionType = "THEME_TOGGLE";
                return y;
            }
            case COLOR_BLUE: {
                Tile b = new Tile(TileType.COLOR_BLUE, "🔵 Ayarlar", 0, 0, 1, 1);
                b.actionType = "LAUNCHER_SETTINGS";
                return b;
            }
            case LAUNCHER_SETTINGS:
                return new Tile(TileType.LAUNCHER_SETTINGS, "Başlatıcı Ayarları", 0, 0, 1, 1);
            case SYSTEM_SETTINGS:
                return new Tile(TileType.SYSTEM_SETTINGS, "Sistem Ayarları", 0, 0, 1, 1);
            case SPACER:
                return new Tile(TileType.SPACER, "Boşluk", 0, 0, 1, 1);
            default:
                return new Tile(type, type.name(), 0, 0, 1, 1);
        }
    }

    /**
     * Uygulama bilgilerinden 1x1 standart uygulama kutusu üretir.
     */
    public static Tile fromApp(String label, String packageName, String activityName) {
        Tile t = new Tile(TileType.APP, label, 0, 0, 1, 1);
        t.packageName = packageName;
        t.activityName = activityName;
        t.actionType = "APP";
        return t;
    }

    public static Tile fromAppInfo(com.safekran.launcher.data.AppInfo ai) {
        if (ai == null) return null;
        return fromApp(ai.label, ai.packageName, ai.activityName);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("type", type.name());
        obj.put("packageName", packageName == null ? "" : packageName);
        obj.put("activityName", activityName == null ? "" : activityName);
        obj.put("label", label == null ? "" : label);
        obj.put("actionType", actionType == null ? "" : actionType);
        obj.put("col", col);
        obj.put("row", row);
        obj.put("colSpan", colSpan);
        obj.put("rowSpan", rowSpan);
        return obj;
    }

    public static Tile fromJson(JSONObject obj) {
        if (obj == null) return null;
        Tile t = new Tile();
        t.id = obj.optString("id", UUID.randomUUID().toString());
        String typeStr = obj.optString("type", "APP");
        if ("EDIT_MODE".equals(typeStr)) {
            t.type = TileType.COLOR_RED;
            t.actionType = "NONE";
            t.label = "🔴 Kırmızı Tuş";
        } else {
            try {
                t.type = TileType.valueOf(typeStr);
            } catch (Exception e) {
                t.type = TileType.APP;
            }
        }
        t.packageName = obj.optString("packageName", "");
        t.activityName = obj.optString("activityName", "");
        t.label = obj.optString("label", "");
        t.actionType = obj.optString("actionType", "");

        // Yeni matris koordinatlarını oku
        if (obj.has("col")) {
            t.col = obj.optInt("col", 0);
            t.row = obj.optInt("row", 0);
            t.colSpan = Math.max(1, obj.optInt("colSpan", 1));
            t.rowSpan = Math.max(1, obj.optInt("rowSpan", 1));
        } else {
            // Eski float sürümden gelen yedekler için otomatik dönüştürme (Geriye Uyumluluk)
            double xPct = obj.optDouble("xPercent", 0.0);
            double yPct = obj.optDouble("yPercent", 0.0);
            double wPct = obj.optDouble("widthPercent", 0.10);
            double hPct = obj.optDouble("heightPercent", 0.10);

            t.col = (int) Math.round(xPct / 0.11);
            t.row = (int) Math.round(yPct / 0.11);
            t.colSpan = (wPct > 0.15) ? 2 : 1;
            t.rowSpan = (hPct > 0.15) ? 2 : 1;
        }

        return t;
    }
}
