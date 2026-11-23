package com.safekran.launcher.util;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;
import java.util.Locale;
import java.util.Set;

/**
 * Saf Ekran - Merkezi Donanım ve Sistem Durumu İzleme Yardımcısı
 * Pil, RAM, Depolama, Ağ ve Bluetooth durum sorgularını tekilleştirir.
 */
public final class SystemInfoHelper {

    private SystemInfoHelper() {
        // Yardımcı sınıf, örneklenemez
    }

    public static class BatteryState {
        public final int percent;
        public final boolean isCharging;
        public final boolean isPresent;
        public final int plugged;

        public BatteryState(int percent, boolean isCharging, boolean isPresent, int plugged) {
            this.percent = percent;
            this.isCharging = isCharging;
            this.isPresent = isPresent;
            this.plugged = plugged;
        }
    }

    public static class RamState {
        public final int usedPercent;
        public final double usedGb;
        public final double totalGb;

        public RamState(int usedPercent, double usedGb, double totalGb) {
            this.usedPercent = usedPercent;
            this.usedGb = usedGb;
            this.totalGb = totalGb;
        }
    }

    public static class StatusDisplay {
        public final String primary;
        public final String secondary;
        public final int color;

        public StatusDisplay(String primary, String secondary, int color) {
            this.primary = primary;
            this.secondary = secondary;
            this.color = color;
        }
    }

    public static BatteryState getBatteryState(Context context) {
        int level = -1, scale = -1, status = -1, plugged = -1;
        boolean isPresent = true;
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                isPresent = batteryStatus.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
                plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            }
        } catch (Exception ignored) {}

        boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL);
        int pct = (level >= 0 && scale > 0) ? Math.round((level / (float) scale) * 100) : 100;
        return new BatteryState(pct, isCharging, isPresent, plugged);
    }

    public static RamState getRamState(Context context) {
        int usedPct = 0;
        double usedGb = 0, totalGb = 0;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am != null) {
                am.getMemoryInfo(mi);
                long totalMem = mi.totalMem;
                long availMem = mi.availMem;
                long usedMem = totalMem - availMem;
                usedPct = (totalMem > 0) ? (int) Math.round((usedMem / (double) totalMem) * 100) : 0;
                usedGb = usedMem / (1024.0 * 1024.0 * 1024.0);
                totalGb = totalMem / (1024.0 * 1024.0 * 1024.0);
            }
        } catch (Exception ignored) {}
        return new RamState(usedPct, usedGb, totalGb);
    }

    public static long getFreeStorageGb() {
        long freeGb = 0;
        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize, availableBlocks;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                blockSize = stat.getBlockSizeLong();
                availableBlocks = stat.getAvailableBlocksLong();
            } else {
                blockSize = stat.getBlockSize();
                availableBlocks = stat.getAvailableBlocks();
            }
            freeGb = (availableBlocks * blockSize) / (1024L * 1024L * 1024L);
        } catch (Exception ignored) {}
        return freeGb;
    }

    public static StatusDisplay getNetworkDisplay(Context context, int defaultTextColor) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = (cm != null) ? cm.getActiveNetworkInfo() : null;
            if (activeNetwork != null && activeNetwork.isConnected()) {
                if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                    WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wi = (wm != null) ? wm.getConnectionInfo() : null;
                    int levelPct = 100;
                    String ipStr = "Wi-Fi Bağlı";
                    if (wi != null) {
                        levelPct = WifiManager.calculateSignalLevel(wi.getRssi(), 100);
                        int ip = wi.getIpAddress();
                        if (ip != 0) {
                            ipStr = String.format(Locale.US, "%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
                        }
                    }
                    return new StatusDisplay("📶 Wi-Fi: %" + levelPct, ipStr, defaultTextColor);
                } else if (activeNetwork.getType() == 9 /* TYPE_ETHERNET */) {
                    return new StatusDisplay("🌐 Ethernet", "Kablolu Bağlı", defaultTextColor);
                } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                    String sub = activeNetwork.getSubtypeName() != null ? activeNetwork.getSubtypeName() : "Bağlı";
                    return new StatusDisplay("📶 Mobil Veri", sub, defaultTextColor);
                } else {
                    return new StatusDisplay("🌐 Bağlı", "İnternet Aktif", defaultTextColor);
                }
            } else {
                return new StatusDisplay("❌ İnternet Yok", "Bağlantı Kesildi", UiUtils.COLOR_RED_ALERT);
            }
        } catch (Exception e) {
            return new StatusDisplay("Ağ Durumu", "Ayarlar", defaultTextColor);
        }
    }

    public static StatusDisplay getBluetoothDisplay(Context context, boolean isLightMode, int defaultTextColor) {
        try {
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            if (ba == null) {
                return new StatusDisplay("Bluetooth Yok", "Donanım Yok", isLightMode ? 0xFF888888 : 0xFF666666);
            } else if (!ba.isEnabled()) {
                return new StatusDisplay("Bluetooth", "Kapalı", isLightMode ? 0xFF888888 : 0xFF777777);
            } else {
                String deviceText = "Bağlı Cihaz Yok";
                try {
                    Set<BluetoothDevice> bonded = ba.getBondedDevices();
                    if (bonded != null && !bonded.isEmpty()) {
                        for (BluetoothDevice dev : bonded) {
                            int a2dp = ba.getProfileConnectionState(BluetoothProfile.A2DP);
                            int headset = ba.getProfileConnectionState(BluetoothProfile.HEADSET);
                            if (a2dp == BluetoothProfile.STATE_CONNECTED || headset == BluetoothProfile.STATE_CONNECTED) {
                                deviceText = dev.getName() != null ? dev.getName() : "Bağlı";
                                break;
                            }
                        }
                        if ("Bağlı Cihaz Yok".equals(deviceText)) {
                            deviceText = bonded.size() + " Eşleşmiş Cihaz";
                        }
                    }
                } catch (Exception ignored) {}
                return new StatusDisplay("🔵 Bluetooth", deviceText, UiUtils.COLOR_BLUE_LIGHT);
            }
        } catch (Exception e) {
            return new StatusDisplay("Bluetooth", "Ayarlar", defaultTextColor);
        }
    }
}
