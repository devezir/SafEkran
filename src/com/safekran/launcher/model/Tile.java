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
