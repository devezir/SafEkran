package com.safekran.launcher.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppScanner {

    public static List<AppInfo> scanAllApps(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, AppInfo> appMap = new HashMap<String, AppInfo>();
        String selfPkg = context.getPackageName();

        // 1. TV Leanback uygulamalarını tara
        Intent leanbackIntent = new Intent(Intent.ACTION_MAIN, null);
        leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> leanbackApps = pm.queryIntentActivities(leanbackIntent, 0);
        if (leanbackApps != null) {
            for (ResolveInfo ri : leanbackApps) {
                if (ri.activityInfo != null && !ri.activityInfo.packageName.equals(selfPkg)) {
                    String pkg = ri.activityInfo.packageName;
                    String act = ri.activityInfo.name;
                    CharSequence labelSeq = ri.loadLabel(pm);
                    String label = (labelSeq != null) ? labelSeq.toString() : pkg;
                    Drawable icon = ri.loadIcon(pm);
                    appMap.put(pkg, new AppInfo(pkg, act, label, icon));
                }
            }
        }

        // 2. Telefon / Dokunmatik / Sideload uygulamalarını tara (Hiçbir ayrım yapmadan)
        Intent standardIntent = new Intent(Intent.ACTION_MAIN, null);
        standardIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> standardApps = pm.queryIntentActivities(standardIntent, 0);
        if (standardApps != null) {
            for (ResolveInfo ri : standardApps) {
                if (ri.activityInfo != null && !ri.activityInfo.packageName.equals(selfPkg)) {
                    String pkg = ri.activityInfo.packageName;
                    if (!appMap.containsKey(pkg)) {
                        String act = ri.activityInfo.name;
                        CharSequence labelSeq = ri.loadLabel(pm);
                        String label = (labelSeq != null) ? labelSeq.toString() : pkg;
                        Drawable icon = ri.loadIcon(pm);
                        appMap.put(pkg, new AppInfo(pkg, act, label, icon));
                    }
                }
            }
        }

        List<AppInfo> result = new ArrayList<AppInfo>(appMap.values());
        Collections.sort(result, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo o1, AppInfo o2) {
                return o1.label.compareToIgnoreCase(o2.label);
            }
        });

        return result;
    }
}
