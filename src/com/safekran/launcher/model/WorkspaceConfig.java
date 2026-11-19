package com.safekran.launcher.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class WorkspaceConfig {
    public int schemaVersion = 1;
    public String appName = "Saf Ekran";
    public String developer = "Vezir";

    public SafeArea safeArea = new SafeArea();
    public float gutterPercent = 0.01f;     // Varsayılan %1
    public float defaultTileWidth = 0.10f;   // Varsayılan %10
    public float defaultTileHeight = 0.10f;  // Varsayılan %10

    public List<Tile> tiles = new ArrayList<>();

    public JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", schemaVersion);
        root.put("appName", appName);
        root.put("developer", developer);
        root.put("safeArea", safeArea.toJson());
        root.put("gutterPercent", (double) gutterPercent);
        root.put("defaultTileWidth", (double) defaultTileWidth);
        root.put("defaultTileHeight", (double) defaultTileHeight);

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

        config.schemaVersion = root.optInt("schemaVersion", 1);
        config.appName = root.optString("appName", "Saf Ekran");
        config.developer = root.optString("developer", "Vezir");

        JSONObject saObj = root.optJSONObject("safeArea");
        if (saObj != null) {
            config.safeArea = SafeArea.fromJson(saObj);
        }

        config.gutterPercent = (float) root.optDouble("gutterPercent", 0.01);
        config.defaultTileWidth = (float) root.optDouble("defaultTileWidth", 0.10);
        config.defaultTileHeight = (float) root.optDouble("defaultTileHeight", 0.10);

        JSONArray arr = root.optJSONArray("tiles");
        config.tiles = new ArrayList<>();
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
