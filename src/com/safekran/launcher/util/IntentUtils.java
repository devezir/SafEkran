package com.safekran.launcher.util;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;

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
     * Verilen eylemleri (action) sırayla dener, hiçbiri açılamazsa genel sistem ayarlarına fallback yapar.
     */
    public static boolean openSettingOrFallback(Context context, String... actions) {
        if (context == null) return false;
        if (actions != null) {
            for (String action : actions) {
                if (action != null && launchSafely(context, new Intent(action))) {
                    return true;
                }
            }
        }
        openSystemSettings(context);
        return false;
    }

    /**
     * Genel Sistem Ayarlarını açar (Öncelik: Android TV -> Standart Ayarlar -> Fallback).
     */
    public static void openSystemSettings(Context context) {
        if (context == null) return;
        if (openTvSettings(context)) return;

        if (launchSafely(context, new Intent(Settings.ACTION_SETTINGS))) return;
        if (launchSafely(context, new Intent("android.settings.SETTINGS"))) return;

        UiUtils.showToast(context, "Sistem ayarları açılamadı.");
    }

    /**
     * Pil / Güç ayarlarını açar.
     */
    public static void openBatterySettings(Context context) {
        if (context == null) return;
        if (openTvSettings(context)) return;
        openSettingOrFallback(context, Intent.ACTION_POWER_USAGE_SUMMARY);
    }

    /**
     * RAM / Uygulama Yönetimi ayarlarını açar.
     */
    public static void openRamSettings(Context context) {
        openSettingOrFallback(context, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
    }

    /**
     * Depolama ayarlarını açar.
     */
    public static void openStorageSettings(Context context) {
        openSettingOrFallback(context, Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
    }

    /**
     * Ağ / Wi-Fi ayarlarını açar.
     */
    public static void openNetworkSettings(Context context) {
        openSettingOrFallback(context, Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS);
    }

    /**
     * Bluetooth ayarlarını açar.
     */
    public static void openBluetoothSettings(Context context) {
        openSettingOrFallback(context, Settings.ACTION_BLUETOOTH_SETTINGS);
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

        openSettingOrFallback(context, Settings.ACTION_DATE_SETTINGS);
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
        UiUtils.showToast(context, "Takvim uygulaması bulunamadı.");
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
        UiUtils.showToastLong(context, "Açılan ekranda 'Başlatıcı' (Home) seçeneğine dokunup 'Saf Ekran'ı seçiniz.");

        // 1. MIUI / HyperOS Özel Varsayılan Uygulamalar İntenti
        if (launchSafely(context, new Intent("miui.intent.action.PREFERRED_APP_SETTINGS"))) return;

        // 2. Xiaomi Settings PreferredSettingsActivity
        Intent miuiComponent = new Intent();
        miuiComponent.setClassName("com.android.settings", "com.android.settings.Settings$PreferredSettingsActivity");
        if (launchSafely(context, miuiComponent)) return;

        // 3. Standart Varsayılan Uygulamalar veya Genel Fallback
        openSettingOrFallback(context, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_HOME_SETTINGS);
    }

    /**
     * Cihazın Android TV / Google TV / Leanback tabanlı olup olmadığını tespit eder.
     */
    public static boolean isTv(Context context) {
        if (context == null) return false;
        try {
            UiModeManager umm = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
            if (umm != null && umm.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
                return true;
            }
            PackageManager pm = context.getPackageManager();
            if (pm != null) {
                return pm.hasSystemFeature("android.software.leanback")
                        || pm.hasSystemFeature("android.hardware.type.television");
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Sistemde şu an varsayılan olarak tanımlı olan ana ekran uygulamasının paket adını döner.
     */
    public static String getCurrentHomePackage(Context context) {
        if (context == null) return null;
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(
                    intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                return resolveInfo.activityInfo.packageName;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Tüm Android sürümlerinde evrensel sistem seçicisini (Chooser) tetikler.
     */
    public static boolean triggerHomeChooser(Activity activity) {
        if (activity == null) return false;
        try {
            try {
                activity.getPackageManager().clearPackagePreferredActivities(activity.getPackageName());
            } catch (Exception ignored) {}

            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Intent chooser = Intent.createChooser(homeIntent, "Varsayılan Başlatıcıyı Seçin");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(chooser);
            return true;
        } catch (Exception e) {
            UiUtils.showToast(activity, "Sistem seçicisi açılamadı.");
            return false;
        }
    }

    /**
     * Android 10+ (Q - Android 17+) Google RoleManager resmi onay dialoğunu açar.
     */
    public static boolean triggerRoleManagerHome(Activity activity) {
        if (activity == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                android.app.role.RoleManager roleManager = (android.app.role.RoleManager) activity.getSystemService(Context.ROLE_SERVICE);
                if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                        Intent roleIntent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME);
                        activity.startActivity(roleIntent);
                        return true;
                    } else {
                        UiUtils.showToast(activity, "Saf Ekran zaten varsayılan başlatıcı.");
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }
        UiUtils.showToastLong(activity, "Bu cihazda Google RoleManager penceresi desteklenmiyor. Diğer seçenekleri deneyiniz.");
        return false;
    }

    /**
     * Android TV & Google TV Çözümü: Mevcut başlatıcının Uygulama Bilgisi ekranını açarak 'Varsayılanları Temizle' dedirtir.
     */
    public static boolean openCurrentLauncherDetails(Activity activity) {
        if (activity == null) return false;
        String curHome = getCurrentHomePackage(activity);
        if (curHome != null && !activity.getPackageName().equals(curHome)) {
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + curHome));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (launchSafely(activity, intent)) {
                    UiUtils.showToastLong(activity, "Açılan ekranda 'Varsayılanları Temizle'ye basıp ardından Kumandadan HOME tuşuna basınız.");
                    return true;
                }
            } catch (Exception ignored) {}
        }
        // Eğer mevcut başlatıcı özel olarak bulunamazsa genel uygulama ayarlarını aç
        openRamSettings(activity);
        UiUtils.showToastLong(activity, "Mevcut başlatıcınızı seçip 'Varsayılanları Temizle' butonuna basınız.");
        return true;
    }

    /**
     * Sistem Ana Ekran Ayarları sayfalarını sırayla açmayı dener.
     */
    public static boolean openHomeSettings(Context context) {
        return openSettingOrFallback(context, Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
    }

    /**
     * Kullanıcıya Saf Ekran'ı varsayılan başlatıcı yapması için uygun akıllı sistem yönlendirmesini uygular.
     */
    public static void promptSetDefaultLauncher(Activity activity) {
        if (activity == null) return;

        // 0. Xiaomi HyperOS / MIUI Tespiti
        if (isXiaomiOrHyperOS()) {
            openXiaomiDefaultAppsSettings(activity);
            return;
        }

        // 1. Android TV / Google TV Tespiti:
        // TV'de RoleManager ekranı anında çöker/kapanır; bu yüzden doğrudan TV Chooser ve TV Ayarlarını deneriz
        if (isTv(activity)) {
            if (triggerHomeChooser(activity)) return;
            if (openHomeSettings(activity)) return;
            openCurrentLauncherDetails(activity);
            return;
        }

        // 2. Mobil Android 10+ (API 29+): RoleManager.ROLE_HOME
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                android.app.role.RoleManager roleManager = (android.app.role.RoleManager) activity.getSystemService(Context.ROLE_SERVICE);
                if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                        Intent roleIntent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME);
                        activity.startActivity(roleIntent);
                        return;
                    } else {
                        UiUtils.showToast(activity, "Saf Ekran zaten varsayılan başlatıcı.");
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 3. Android 7.0 - 9.0: ACTION_HOME_SETTINGS
        if (openHomeSettings(activity)) return;

        // 4. Android 5.0 - 6.0 ve Genel Fallback: HOME Intent ile Sistem Seçicisi
        if (triggerHomeChooser(activity)) return;

        // 5. Genel Sistem Ayarları Fallback
        openSystemSettings(activity);
    }
}
