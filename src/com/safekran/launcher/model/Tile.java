package com.safekran.launcher.model;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class Tile {
    public String id;
    public TileType type;
    public String packageName;
    public String activityName;
    public String label;

    // Seçenek B: Serbest Yüzde (Ekranın genişlik ve yüksekliğine göre %10x%10 esner)
    public float xPercent;
    public float yPercent;
    public float widthPercent;
    public float heightPercent;

    public Tile() {
        this.id = UUID.randomUUID().toString();
        this.type = TileType.APP;
        this.widthPercent = 0.10f;  // Varsayılan %10
        this.heightPercent = 0.10f; // Varsayılan %10
    }

    public Tile(TileType type, String label, float x, float y, float w, float h) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.label = label;
        this.xPercent = x;
        this.yPercent = y;
        this.widthPercent = w;
        this.heightPercent = h;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("type", type.name());
        obj.put("packageName", packageName == null ? "" : packageName);
        obj.put("activityName", activityName == null ? "" : activityName);
        obj.put("label", label == null ? "" : label);
        obj.put("xPercent", (double) xPercent);
        obj.put("yPercent", (double) yPercent);
        obj.put("widthPercent", (double) widthPercent);
        obj.put("heightPercent", (double) heightPercent);
        return obj;
    }

    public static Tile fromJson(JSONObject obj) {
        if (obj == null) return null;
        Tile t = new Tile();
        t.id = obj.optString("id", UUID.randomUUID().toString());
        String typeStr = obj.optString("type", "APP");
        try {
            t.type = TileType.valueOf(typeStr);
        } catch (Exception e) {
            t.type = TileType.APP;
        }
        t.packageName = obj.optString("packageName", "");
        t.activityName = obj.optString("activityName", "");
        t.label = obj.optString("label", "");
        t.xPercent = (float) obj.optDouble("xPercent", 0.0);
        t.yPercent = (float) obj.optDouble("yPercent", 0.0);
        t.widthPercent = (float) obj.optDouble("widthPercent", 0.10);
        t.heightPercent = (float) obj.optDouble("heightPercent", 0.10);
        return t;
    }
}
