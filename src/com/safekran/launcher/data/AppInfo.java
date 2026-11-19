package com.safekran.launcher.data;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public String packageName;
    public String activityName;
    public String label;
    public Drawable icon;

    public AppInfo(String packageName, String activityName, String label, Drawable icon) {
        this.packageName = packageName;
        this.activityName = activityName;
        this.label = label;
        this.icon = icon;
    }
}
