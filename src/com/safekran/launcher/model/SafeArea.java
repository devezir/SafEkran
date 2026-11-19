package com.safekran.launcher.model;

import org.json.JSONException;
import org.json.JSONObject;

public class SafeArea {
    public float top;
    public float bottom;
    public float left;
    public float right;

    public SafeArea() {
        this(0.03f, 0.03f, 0.03f, 0.03f); // Varsayılan Optimum %3
    }

    public SafeArea(float top, float bottom, float left, float right) {
        this.top = top;
        this.bottom = bottom;
        this.left = left;
        this.right = right;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("top", (double) top);
        obj.put("bottom", (double) bottom);
        obj.put("left", (double) left);
        obj.put("right", (double) right);
        return obj;
    }

    public static SafeArea fromJson(JSONObject obj) {
        SafeArea sa = new SafeArea();
        if (obj != null) {
            sa.top = (float) obj.optDouble("top", 0.03);
            sa.bottom = (float) obj.optDouble("bottom", 0.03);
            sa.left = (float) obj.optDouble("left", 0.03);
            sa.right = (float) obj.optDouble("right", 0.03);
        }
        return sa;
    }
}
