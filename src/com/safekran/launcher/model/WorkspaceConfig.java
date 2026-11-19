package com.safekran.launcher.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Saf Ekran Otomatik Türetilmiş Izgara Yapılandırması (v4).
 * Sütun sayısı yapay bir ayar değildir; Ekran Genişliği, Kutu Boyutu 
 * ve Gutter oranından matematiksel olarak türetilir.
 */
public class WorkspaceConfig {
    public int schemaVersion = 4;
    public String appName = "Saf Ekran";
    public String developer = "Vezir";

    public SafeArea safeArea = new SafeArea();
    
    // Temel Geometrik Değişkenler
    public float tileScale = 0.10f;          // Referans boyuta göre Kutu Genişliği (Varsayılan %10)
    public float tileHeightRatio = 0.75f;     // Kutu Yükseklik Oranı (Genişliğin %75'i - Şık TV kartı)
    public float gutterPercent = 0.01f;      // Kutular arası boşluk (Varsayılan %1)
    public float focusScale = 1.06f;          // Odaklanınca büyüme payı (%6)

    public String tileStyle = "FOCUS_ONLY";   // "FOCUS_ONLY", "ALWAYS", "FLOATING"
    public String themeMode = "BLACK";        // "BLACK", "DARK_GRAY", "LIGHT"
    public boolean is24Hour = true;
    public boolean showDate = true;

    public List<Tile> tiles = new ArrayList<Tile>();

    public JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", schemaVersion);
        root.put("appName", appName);
        root.put("developer", developer);
        root.put("safeArea", safeArea.toJson());
        root.put("tileScale", (double) tileScale);
        root.put("tileHeightRatio", (double) tileHeightRatio);
        root.put("gutterPercent", (double) gutterPercent);
        root.put("focusScale", (double) focusScale);
        root.put("tileStyle", tileStyle);
        root.put("themeMode", themeMode);
        root.put("is24Hour", is24Hour);
        root.put("showDate", showDate);

        JSONArray arr = new JSONArray();
        for (Tile t : tiles) {
            arr.put(t.toJson());
        }
        root.put("tiles", arr);
        return root;
    }

    public static WorkspaceConfig fromJson(JSONObject root) {
        WorkspaceConfig config = new WorkspaceConfig();
        if (root == null) return config;

        config.schemaVersion = root.optInt("schemaVersion", 4);
        config.appName = root.optString("appName", "Saf Ekran");
        config.developer = root.optString("developer", "Vezir");

        JSONObject saObj = root.optJSONObject("safeArea");
        if (saObj != null) {
            config.safeArea = SafeArea.fromJson(saObj);
        }

        config.tileScale = (float) root.optDouble("tileScale", 0.10);
        config.tileHeightRatio = (float) root.optDouble("tileHeightRatio", 0.75);
        config.gutterPercent = (float) root.optDouble("gutterPercent", 0.01);
        config.focusScale = (float) root.optDouble("focusScale", 1.06);
        config.tileStyle = root.optString("tileStyle", "FOCUS_ONLY");
        config.themeMode = root.optString("themeMode", "BLACK");
        config.is24Hour = root.optBoolean("is24Hour", true);
        config.showDate = root.optBoolean("showDate", true);

        JSONArray arr = root.optJSONArray("tiles");
        config.tiles = new ArrayList<Tile>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject tObj = arr.optJSONObject(i);
                if (tObj != null) {
                    Tile t = Tile.fromJson(tObj);
                    if (t != null) {
                        config.tiles.add(t);
                    }
                }
            }
        }
        return config;
    }
}
