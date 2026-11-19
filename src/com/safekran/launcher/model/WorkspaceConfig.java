package com.safekran.launcher.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class WorkspaceConfig {
    public int schemaVersion = 3; // Superpowers 2D Matris Sürümü
    public String appName = "Saf Ekran";
    public String developer = "Vezir";

    public SafeArea safeArea = new SafeArea();
    public int colCount = 8;                 // Varsayılan 8 Sütun (TV için ideal)
    public float gutterPercent = 0.01f;     // Varsayılan %1
    public float defaultTileHeight = 0.10f;  // Varsayılan %10 (TV 16:9 dikdörtgen)
    public float focusScale = 1.06f;         // Varsayılan %6 büyüme

    public String themeMode = "BLACK";       // "BLACK", "DARK_GRAY", "LIGHT"
    public boolean is24Hour = true;
    public boolean showDate = true;

    public List<Tile> tiles = new ArrayList<Tile>();

    public JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", schemaVersion);
        root.put("appName", appName);
        root.put("developer", developer);
        root.put("safeArea", safeArea.toJson());
        root.put("colCount", colCount);
        root.put("gutterPercent", (double) gutterPercent);
        root.put("defaultTileHeight", (double) defaultTileHeight);
        root.put("focusScale", (double) focusScale);
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

        config.schemaVersion = root.optInt("schemaVersion", 3);
        config.appName = root.optString("appName", "Saf Ekran");
        config.developer = root.optString("developer", "Vezir");

        JSONObject saObj = root.optJSONObject("safeArea");
        if (saObj != null) {
            config.safeArea = SafeArea.fromJson(saObj);
        }

        config.colCount = root.optInt("colCount", 8);
        config.gutterPercent = (float) root.optDouble("gutterPercent", 0.01);
        config.defaultTileHeight = (float) root.optDouble("defaultTileHeight", 0.10);
        config.focusScale = (float) root.optDouble("focusScale", 1.06);
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
