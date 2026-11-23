package com.safekran.launcher.util;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.widget.Toast;

import java.util.Locale;

/**
 * Saf Ekran - Merkezi Intent ve Sistem Ayarları Yönetimi
 * Tüm platformlar (AOSP, Android TV, Xiaomi HyperOS/MIUI vb.) için güvenli Intent fırlatma,
 * varsayılan başlatıcı kontrolleri ve donanım/sistem ayar yönlendirmelerini içerir.
 */
public final class IntentUtils {

    private IntentUtils() {
        // Yardımcı sınıf, örneklenemez
    }

    /**
     * Verilen Intent'i FLAG_ACTIVITY_NEW_TASK ile güvenli şekilde başlatır.
     * Cihazda bu Intent'i karşılayacak bir Activity olup olmadığını doğrular.
     */
    public static boolean launchSafely(Context context, Intent intent) {
        if (context == null || intent == null) return false;
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (context.getPackageManager().resolveActivity(intent, 0) != null) {
                context.startActivity(intent);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Verilen paket adına ait ana başlatıcı Activity'yi bulup çalıştırır.
     */
    public static boolean launchPackage(Context context, String packageName) {
        if (context == null || packageName == null) return false;
        try {
            Intent pIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (pIntent != null) {
                return launchSafely(context, pIntent);
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Verilen paket listesinden sistemde mevcut olan ilk uygulamayı başlatır.
     */
    public static boolean launchAnyPackage(Context context, String[] packageNames) {
        if (context == null || packageNames == null) return false;
        for (String pkg : packageNames) {
            if (launchPackage(context, pkg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Android TV yerel ayarlar uygulamasını (com.android.tv.settings) açmayı dener.
     */
    public static boolean openTvSettings(Context context) {
        if (context == null) return false;
        try {
            Intent tvIntent = new Intent(Intent.ACTION_MAIN);
            tvIntent.setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings");
            if (launchSafely(context, tvIntent)) return true;
        } catch (Exception ignored) {}

        return launchPackage(context, "com.android.tv.settings");
    }

    /**
     * Genel Sistem Ayarlarını açar (Öncelik: Android TV -> Standart Ayarlar -> Fallback).
     */
    public static void openSystemSettings(Context context) {
        if (context == null) return;
        if (openTvSettings(context)) return;

        if (launchSafely(context, new Intent(Settings.ACTION_SETTINGS))) return;
        if (launchSafely(context, new Intent("android.settings.SETTINGS"))) return;

        Toast.makeText(context, "Sistem ayarları açılamadı.", Toast.LENGTH_SHORT).show();
    }

    /**
     * Pil / Güç ayarlarını açar.
     */
    public static void openBatterySettings(Context context) {
        if (context == null) return;
        if (openTvSettings(context)) return;
        if (launchSafely(context, new Intent(Intent.ACTION_POWER_USAGE_SUMMARY))) return;
        openSystemSettings(context);
    }

    /**
     * RAM / Uygulama Yönetimi ayarlarını açar.
     */
    public static void openRamSettings(Context context) {
        if (context == null) return;
        if (launchSafely(context, new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))) return;
        openSystemSettings(context);
    }

    /**
     * Depolama ayarlarını açar.
     */
    public static void openStorageSettings(Context context) {
        if (context == null) return;
        if (launchSafely(context, new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))) return;
        openSystemSettings(context);
    }

    /**
     * Ağ / Wi-Fi ayarlarını açar.
     */
    public static void openNetworkSettings(Context context) {
        if (context == null) return;
        if (launchSafely(context, new Intent(Settings.ACTION_WIFI_SETTINGS))) return;
        if (launchSafely(context, new Intent(Settings.ACTION_WIRELESS_SETTINGS))) return;
        openSystemSettings(context);
    }

    /**
     * Bluetooth ayarlarını açar.
     */
    public static void openBluetoothSettings(Context context) {
        if (context == null) return;
        if (launchSafely(context, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) return;
        openSystemSettings(context);
    }

    /**
     * Saat / Alarm uygulamasını veya ayarlarını açar.
     */
    public static void openClockApp(Context context) {
        if (context == null) return;
        try {
            Intent clockIntent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
            if (launchSafely(context, clockIntent)) return;
        } catch (Exception ignored) {}

        String[] clockPackages = new String[]{
                "com.google.android.deskclock",
                "com.android.deskclock",
                "com.sec.android.app.clockpackage",
                "com.miui.clock",
                "com.coloros.alarmclock",
                "com.huawei.android.totemweather"
        };
        if (launchAnyPackage(context, clockPackages)) return;

        if (launchSafely(context, new Intent(Settings.ACTION_DATE_SETTINGS))) return;
        openSystemSettings(context);
    }

    /**
     * Takvim uygulamasını veya ayarlarını açar.
     */
    public static void openCalendarApp(Context context) {
        if (context == null) return;
        try {
            Intent calIntent = new Intent(Intent.ACTION_MAIN);
            calIntent.addCategory(Intent.CATEGORY_APP_CALENDAR);
            if (launchSafely(context, calIntent)) return;
        } catch (Exception ignored) {}

        try {
            Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
            builder.appendPath("time");
            ContentUris.appendId(builder, System.currentTimeMillis());
            Intent viewIntent = new Intent(Intent.ACTION_VIEW, builder.build());
            if (launchSafely(context, viewIntent)) return;
        } catch (Exception ignored) {}

        String[] calPackages = new String[]{
                "com.google.android.calendar",
                "com.samsung.android.calendar",
                "com.android.calendar",
                "com.miui.calendar",
                "com.bbk.calendar",
                "com.huawei.calendar"
        };
        if (launchAnyPackage(context, calPackages)) return;

        if (launchSafely(context, new Intent(Settings.ACTION_DATE_SETTINGS))) return;
        Toast.makeText(context, "Takvim uygulaması bulunamadı.", Toast.LENGTH_SHORT).show();
    }

    /**
     * Saf Ekran'ın şu an varsayılan başlatıcı olup olmadığını kontrol eder.
     */
    public static boolean isDefaultLauncher(Context context) {
        if (context == null) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(
                    intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                return context.getPackageName().equals(resolveInfo.activityInfo.packageName);
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Cihazın Xiaomi, Redmi, POCO veya MIUI/HyperOS tabanlı olup olmadığını tespit eder.
     */
    public static boolean isXiaomiOrHyperOS() {
        String man = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(Locale.US) : "";
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase(Locale.US) : "";
        if (man.contains("xiaomi") || man.contains("redmi") || man.contains("poco") ||
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")) {
            return true;
        }
        try {
            Class<?> spCls = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method getMethod = spCls.getMethod("get", String.class);
            String miuiVer = (String) getMethod.invoke(null, "ro.miui.ui.version.name");
            if (miuiVer != null && !miuiVer.trim().isEmpty()) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Xiaomi / HyperOS / MIUI cihazlara özel varsayılan uygulama seçim ekranını açar.
     */
    public static void openXiaomiDefaultAppsSettings(Context context) {
        if (context == null) return;
        Toast.makeText(context, "Açılan ekranda 'Başlatıcı' (Home) seçeneğine dokunup 'Saf Ekran'ı seçiniz.", Toast.LENGTH_LONG).show();

        // 1. MIUI / HyperOS Özel Varsayılan Uygulamalar İntenti
        if (launchSafely(context, new Intent("miui.intent.action.PREFERRED_APP_SETTINGS"))) return;

        // 2. Xiaomi Settings PreferredSettingsActivity
        Intent miuiComponent = new Intent();
        miuiComponent.setClassName("com.android.settings", "com.android.settings.Settings$PreferredSettingsActivity");
        if (launchSafely(context, miuiComponent)) return;

        // 3. Standart Varsayılan Uygulamalar Ekranı
        if (launchSafely(context, new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) return;

        // 4. ACTION_HOME_SETTINGS
        if (launchSafely(context, new Intent(Settings.ACTION_HOME_SETTINGS))) return;

        // 5. Genel Sistem Ayarları Fallback
        openSystemSettings(context);
    }

    /**
     * Kullanıcıya Saf Ekran'ı varsayılan başlatıcı yapması için uygun sistem dialoğunu açar.
     */
    public static void promptSetDefaultLauncher(Activity activity) {
        if (activity == null) return;

        // 0. Xiaomi HyperOS / MIUI Tespiti
        if (isXiaomiOrHyperOS()) {
            openXiaomiDefaultAppsSettings(activity);
            return;
        }

        // 1. Android 10+ (API 29+): RoleManager.ROLE_HOME
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                android.app.role.RoleManager roleManager = (android.app.role.RoleManager) activity.getSystemService(Context.ROLE_SERVICE);
                if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                        Intent roleIntent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME);
                        activity.startActivity(roleIntent);
                        return;
                    } else {
                        Toast.makeText(activity, "Saf Ekran zaten varsayılan başlatıcı.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Android 7.0 - 9.0 (veya RoleManager desteklenmeyen ROM'lar): ACTION_HOME_SETTINGS
        if (launchSafely(activity, new Intent(Settings.ACTION_HOME_SETTINGS))) return;

        // 3. Android TV / AOSP Ayarları
        if (launchSafely(activity, new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) return;

        // 4. Android 5.0 - 6.0 ve Genel Fallback: HOME Intent ile Sistem Seçicisini Tetikleme
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Intent chooser = Intent.createChooser(homeIntent, "Varsayılan Başlatıcıyı Seçin");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(chooser);
            return;
        } catch (Exception ignored) {}

        // 5. Genel Sistem Ayarları Fallback
        openSystemSettings(activity);
    }
}
