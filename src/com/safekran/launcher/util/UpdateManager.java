package com.safekran.launcher.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateManager {

    private static final String GITHUB_API_LATEST = "https://api.github.com/repos/devezir/SafEkran/releases/latest";
    private static final String RAW_VERSION_JSON = "https://raw.githubusercontent.com/devezir/SafEkran/main/version.json";
    private static final String USER_AGENT = "SafEkran-Launcher";

    public static class UpdateInfo {
        public final boolean hasUpdate;
        public final int currentVersionCode;
        public final String currentVersionName;
        public final int remoteVersionCode;
        public final String remoteVersionName;
        public final String releaseTitle;
        public final String releaseNotes;
        public final String downloadUrl;
        public final long fileSize;

        public UpdateInfo(boolean hasUpdate, int currentVersionCode, String currentVersionName,
                          int remoteVersionCode, String remoteVersionName, String releaseTitle,
                          String releaseNotes, String downloadUrl, long fileSize) {
            this.hasUpdate = hasUpdate;
            this.currentVersionCode = currentVersionCode;
            this.currentVersionName = currentVersionName;
            this.remoteVersionCode = remoteVersionCode;
            this.remoteVersionName = remoteVersionName;
            this.releaseTitle = releaseTitle;
            this.releaseNotes = releaseNotes;
            this.downloadUrl = downloadUrl;
            this.fileSize = fileSize;
        }
    }

    public interface CheckCallback {
        void onSuccess(UpdateInfo info);
        void onError(String message);
    }

    public interface DownloadCallback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
        void onCompleted(File apkFile);
        void onError(String message);
    }

    public static void checkForUpdate(final Context context, final CheckCallback callback) {
        final Handler handler = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int localVc = 1;
                    String localVn = "1.0.0";
                    try {
                        PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                        localVc = pInfo.versionCode;
                        localVn = pInfo.versionName;
                    } catch (Exception ignored) {}

                    UpdateInfo info = fetchFromGitHubApi(localVc, localVn);
                    if (info == null) {
                        info = fetchFromVersionJson(localVc, localVn);
                    }

                    if (info == null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError("Güncelleme sunucusuna ulaşılamadı.");
                            }
                        });
                        return;
                    }

                    final UpdateInfo finalInfo = info;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(finalInfo);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError("Hata: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private static UpdateInfo fetchFromGitHubApi(int localVc, String localVn) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(GITHUB_API_LATEST);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            String jsonStr = readStream(conn.getInputStream());
            JSONObject root = new JSONObject(jsonStr);

            String tagName = root.optString("tag_name", "");
            String remoteVn = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            String title = root.optString("name", "Saf Ekran v" + remoteVn);
            String body = root.optString("body", "");

            String downloadUrl = null;
            long fileSize = 0;
            int remoteVc = 0;

            JSONArray assets = root.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String aName = asset.optString("name", "");
                    if (aName.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", null);
                        fileSize = asset.optLong("size", 0);

                        // SafEkran_v2.21.3_vc36.apk -> versionCode = 36
                        if (aName.contains("_vc")) {
                            try {
                                String vcStr = aName.substring(aName.indexOf("_vc") + 3).replaceAll("[^0-9].*", "");
                                remoteVc = Integer.parseInt(vcStr);
                            } catch (Exception ignored) {}
                        }
                        break;
                    }
                }
            }

            if (downloadUrl == null) {
                return null;
            }

            boolean hasUpdate = (remoteVc > localVc) || (remoteVc == 0 && isVersionNewer(remoteVn, localVn));
            return new UpdateInfo(hasUpdate, localVc, localVn, remoteVc, remoteVn, title, body, downloadUrl, fileSize);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static UpdateInfo fetchFromVersionJson(int localVc, String localVn) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(RAW_VERSION_JSON);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            String jsonStr = readStream(conn.getInputStream());
            JSONObject root = new JSONObject(jsonStr);

            int remoteVc = root.optInt("versionCode", 0);
            String remoteVn = root.optString("versionName", "");
            String apkName = root.optString("apkName", "SafEkran.apk");

            String downloadUrl = "https://github.com/devezir/SafEkran/releases/download/v" + remoteVn + "/" + apkName;
            String title = "Saf Ekran v" + remoteVn;
            String body = "Yeni Saf Ekran sürümü yayında.";

            JSONArray history = root.optJSONArray("history");
            if (history != null && history.length() > 0) {
                JSONObject lastHist = history.getJSONObject(history.length() - 1);
                body = lastHist.optString("description", body);
            }

            boolean hasUpdate = (remoteVc > localVc) || (remoteVc == 0 && isVersionNewer(remoteVn, localVn));
            return new UpdateInfo(hasUpdate, localVc, localVn, remoteVc, remoteVn, title, body, downloadUrl, 0);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static void downloadUpdate(final Context context, final String downloadUrl, final DownloadCallback callback) {
        final Handler handler = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                InputStream in = null;
                FileOutputStream out = null;
                try {
                    String currentUrl = downloadUrl;
                    // GitHub yönlendirmelerini takip et (301, 302, 307, 308)
                    for (int r = 0; r < 5; r++) {
                        URL u = new URL(currentUrl);
                        conn = (HttpURLConnection) u.openConnection();
                        conn.setRequestProperty("User-Agent", USER_AGENT);
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(30000);
                        conn.setInstanceFollowRedirects(true);
                        int status = conn.getResponseCode();
                        if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                                status == HttpURLConnection.HTTP_MOVED_PERM ||
                                status == 307 || status == 308) {
                            String loc = conn.getHeaderField("Location");
                            if (loc != null && !loc.isEmpty()) {
                                conn.disconnect();
                                currentUrl = loc;
                                continue;
                            }
                        }
                        break;
                    }

                    if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        throw new Exception("İndirme bağlantısı yanıt vermedi (HTTP " + conn.getResponseCode() + ")");
                    }

                    long totalBytes = -1;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        totalBytes = conn.getContentLengthLong();
                    } else {
                        totalBytes = conn.getContentLength();
                    }

                    File targetFile = ApkProvider.getUpdateApkFile(context);
                    if (targetFile.exists()) {
                        targetFile.delete();
                    }
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    in = conn.getInputStream();
                    out = new FileOutputStream(targetFile);

                    byte[] buffer = new byte[8192];
                    long downloadedBytes = 0;
                    int bytesRead;
                    int lastPct = -1;

                    final long finalTotal = totalBytes;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;

                        final long curDownloaded = downloadedBytes;
                        int pct = finalTotal > 0 ? (int) ((curDownloaded * 100) / finalTotal) : -1;
                        if (pct != lastPct) {
                            lastPct = pct;
                            final int finalPct = pct;
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onProgress(finalPct, curDownloaded, finalTotal);
                                }
                            });
                        }
                    }

                    out.flush();
                    final File finalFile = targetFile;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onCompleted(finalFile);
                        }
                    });

                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(e.getMessage());
                        }
                    });
                } finally {
                    try { if (in != null) in.close(); } catch (Exception ignored) {}
                    try { if (out != null) out.close(); } catch (Exception ignored) {}
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    public static void installApk(Context context, File apkFile) {
        if (apkFile == null || !apkFile.exists() || apkFile.length() < 10000) {
            Toast.makeText(context, "İndirilen APK dosyası doğrulanamadı.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Android 8.0+ (Oreo, API 26+) bilinmeyen kaynak izni
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                try {
                    Intent manageIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    manageIntent.setData(Uri.parse("package:" + context.getPackageName()));
                    manageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(manageIntent);
                    Toast.makeText(context, "Güncellemeyi kurabilmek için lütfen Saf Ekran'a izin verin.", Toast.LENGTH_LONG).show();
                    return;
                } catch (Exception ignored) {}
            }
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = ApkProvider.getApkUri(context);
            } else {
                uri = Uri.fromFile(apkFile);
            }

            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Paket kurucu başlatılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static boolean isVersionNewer(String remote, String local) {
        if (remote == null || local == null) return false;
        try {
            String[] rParts = remote.replaceAll("[^0-9.]", "").split("\\.");
            String[] lParts = local.replaceAll("[^0-9.]", "").split("\\.");
            int max = Math.max(rParts.length, lParts.length);
            for (int i = 0; i < max; i++) {
                int r = i < rParts.length && !rParts[i].isEmpty() ? Integer.parseInt(rParts[i]) : 0;
                int l = i < lParts.length && !lParts[i].isEmpty() ? Integer.parseInt(lParts[i]) : 0;
                if (r > l) return true;
                if (r < l) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static String readStream(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }
}
