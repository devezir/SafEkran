package com.safekran.launcher.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/**
 * Saf Ekran - OTA Güncelleme Arayüz ve Akış Yöneticisi
 * Güncelleme kontrol spinner'ı, sürüm detayları, indirme ilerleme çubuğu ve izin akışlarını yönetir.
 */
public final class UpdateFlowManager {

    private static final String PREF_NAME = "safekran_update";
    private static final String PREF_KEY_PENDING = "pending_install_flow";
    private static final String PREF_KEY_VC = "pending_remote_vc";
    private static final String PREF_KEY_VN = "pending_remote_vn";
    private static final String PREF_KEY_URL = "pending_dl_url";

    private UpdateFlowManager() {
        // Yardımcı sınıf, örneklenemez
    }

    public static void startUpdateCheckFlow(final Activity activity, final float sScale) {
        startCheckFlow(activity, sScale);
    }

    public static void startCheckFlow(final Activity activity, final float sScale) {
        if (activity == null || activity.isFinishing()) return;

        final AlertDialog checkDialog = new AlertDialog.Builder(activity).create();
        LinearLayout checkLayout = new LinearLayout(activity);
        checkLayout.setOrientation(LinearLayout.HORIZONTAL);
        checkLayout.setGravity(Gravity.CENTER_VERTICAL);
        int pad = UiUtils.dpToPx(activity, 20);
        checkLayout.setPadding(pad, pad, pad, pad);

        ProgressBar spinner = new ProgressBar(activity);
        checkLayout.addView(spinner);

        TextView checkMsg = new TextView(activity);
        checkMsg.setText("   Güncellemeler denetleniyor...");
        checkMsg.setTextColor(Color.WHITE);
        checkMsg.setTextSize(Math.round(13 * sScale));
        checkLayout.addView(checkMsg);

        checkDialog.setView(checkLayout);
        checkDialog.setCancelable(false);
        checkDialog.show();

        UpdateManager.checkForUpdate(activity, new UpdateManager.CheckCallback() {
            @Override
            public void onSuccess(final UpdateManager.UpdateInfo info) {
                if (checkDialog.isShowing()) checkDialog.dismiss();

                if (!info.hasUpdate) {
                    AlertDialog.Builder alert = new AlertDialog.Builder(activity);
                    alert.setTitle("✅ En Güncel Sürümdesiniz");
                    alert.setMessage("Mevcut Sürüm: v" + info.currentVersionName + " (Yapı: " + info.currentVersionCode + ")\n\nSaf Ekran en güncel sürümdedir.");
                    alert.setPositiveButton("Tamam", null);
                    alert.show();
                    return;
                }

                showUpdateAvailableDialog(activity, info, sScale);
            }

            @Override
            public void onError(final String message) {
                if (checkDialog.isShowing()) checkDialog.dismiss();
                AlertDialog.Builder alert = new AlertDialog.Builder(activity);
                alert.setTitle("⚠️ Güncelleme Denetlenemedi");
                alert.setMessage("Güncelleme sunucusuna bağlanırken hata oluştu:\n" + message + "\n\nLütfen internet bağlantınızı kontrol edip tekrar deneyin.");
                alert.setPositiveButton("Tamam", null);
                alert.show();
            }
        });
    }

    public static void showUpdateAvailableDialog(final Activity activity, final UpdateManager.UpdateInfo info, final float sScale) {
        if (activity == null || activity.isFinishing()) return;

        final AlertDialog updateDialog = new AlertDialog.Builder(activity).create();
        ScrollView sv = new ScrollView(activity);
        LinearLayout uLayout = new LinearLayout(activity);
        uLayout.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dpToPx(activity, 16);
        uLayout.setPadding(pad, pad, pad, pad);

        TextView titleView = new TextView(activity);
        titleView.setText("🎉 Yeni Sürüm Mevcut: v" + info.remoteVersionName);
        titleView.setTextColor(UiUtils.COLOR_GREEN_SUCCESS);
        titleView.setTextSize(Math.round(15 * sScale));
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setPadding(0, 0, 0, UiUtils.dpToPx(activity, 6));
        uLayout.addView(titleView);

        TextView versionSub = new TextView(activity);
        String sizeStr = info.fileSize > 0 ? " (" + (info.fileSize / 1024) + " KB)" : "";
        versionSub.setText("Kurulu: v" + info.currentVersionName + " ➔ Yeni: v" + info.remoteVersionName + sizeStr);
        versionSub.setTextColor(UiUtils.COLOR_CYAN_LIGHT);
        versionSub.setTextSize(Math.round(11 * sScale));
        versionSub.setPadding(0, 0, 0, UiUtils.dpToPx(activity, 10));
        uLayout.addView(versionSub);

        if (info.releaseNotes != null && !info.releaseNotes.trim().isEmpty()) {
            TextView notesTitle = new TextView(activity);
            notesTitle.setText("📋 Sürüm Notları / Yenilikler:");
            notesTitle.setTextColor(Color.parseColor("#FFD54F"));
            notesTitle.setTextSize(Math.round(12 * sScale));
            notesTitle.setTypeface(Typeface.DEFAULT_BOLD);
            notesTitle.setPadding(0, 0, 0, UiUtils.dpToPx(activity, 4));
            uLayout.addView(notesTitle);

            TextView notesBody = new TextView(activity);
            notesBody.setText(info.releaseNotes.trim());
            notesBody.setTextColor(UiUtils.COLOR_TEXT_LIGHT_GRAY);
            notesBody.setTextSize(Math.round(11 * sScale));
            notesBody.setPadding(UiUtils.dpToPx(activity, 10), UiUtils.dpToPx(activity, 8), UiUtils.dpToPx(activity, 10), UiUtils.dpToPx(activity, 10));

            GradientDrawable notesBg = new GradientDrawable();
            notesBg.setColor(Color.parseColor("#1C2A38"));
            notesBg.setCornerRadius(UiUtils.dpToPx(activity, 6));
            notesBody.setBackground(notesBg);
            uLayout.addView(notesBody);
        }

        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, UiUtils.dpToPx(activity, 14), 0, 0);

        final Button dlBtn = new Button(activity);
        dlBtn.setText("📥 Hemen İndir ve Kur");
        dlBtn.setTextSize(Math.round(11 * sScale));
        dlBtn.setTextColor(Color.WHITE);
        UiUtils.setupTvFocusToggleButton(dlBtn, true, 4);
        LinearLayout.LayoutParams dlLp = new LinearLayout.LayoutParams(0, (int) (UiUtils.dpToPx(activity, 42) * Math.min(1.3f, sScale)), 1.2f);
        dlLp.rightMargin = UiUtils.dpToPx(activity, 4);
        dlBtn.setLayoutParams(dlLp);

        Button laterBtn = new Button(activity);
        laterBtn.setText("Daha Sonra");
        laterBtn.setTextSize(Math.round(11 * sScale));
        laterBtn.setTextColor(UiUtils.COLOR_TEXT_LIGHT_GRAY);
        UiUtils.setupTvFocusHighlight(laterBtn, UiUtils.COLOR_FOCUS_DARK, 4);
        LinearLayout.LayoutParams ltLp = new LinearLayout.LayoutParams(0, (int) (UiUtils.dpToPx(activity, 42) * Math.min(1.3f, sScale)), 0.8f);
        laterBtn.setLayoutParams(ltLp);

        btnRow.addView(dlBtn);
        btnRow.addView(laterBtn);
        uLayout.addView(btnRow);

        sv.addView(uLayout);
        updateDialog.setView(sv);

        updateDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                dlBtn.requestFocus();
            }
        });

        dlBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateDialog.dismiss();
                if (!UpdateManager.hasInstallPermission(activity)) {
                    showInstallPermissionGuideDialog(activity, info, sScale);
                } else {
                    startDownloadFlow(activity, info, sScale);
                }
            }
        });

        laterBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateDialog.dismiss();
            }
        });

        updateDialog.show();
    }

    public static void showInstallPermissionGuideDialog(final Activity activity, final UpdateManager.UpdateInfo info, final float sScale) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("⚠️ Kurulum İzni Gerekli");
        builder.setMessage("Saf Ekran güncellemelerini kurabilmek için sonraki ekranda 'Bu kaynaktan izin ver' anahtarını açmanız gerekmektedir.\n\nİzni açıp geri döndüğünüzde indirme ve kurulum otomatik olarak başlayacaktır.");
        builder.setPositiveButton("İzin Ver ve Devam Et", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                        .putBoolean(PREF_KEY_PENDING, true)
                        .putInt(PREF_KEY_VC, info.remoteVersionCode)
                        .putString(PREF_KEY_VN, info.remoteVersionName)
                        .putString(PREF_KEY_URL, info.downloadUrl)
                        .apply();
                UpdateManager.requestInstallPermission(activity);
            }
        });
        builder.setNegativeButton("Vazgeç", null);
        builder.create().show();
    }

    public static void checkPendingUpdateOnResume(final Activity activity, final float sScale) {
        if (activity == null) return;
        SharedPreferences sp = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean pending = sp.getBoolean(PREF_KEY_PENDING, false);
        if (!pending) return;

        if (UpdateManager.hasInstallPermission(activity)) {
            sp.edit().remove(PREF_KEY_PENDING).apply();

            int remoteVc = sp.getInt(PREF_KEY_VC, 0);
            String remoteVn = sp.getString(PREF_KEY_VN, "");
            String dlUrl = sp.getString(PREF_KEY_URL, "");

            File apkFile = ApkProvider.getUpdateApkFile(activity);
            if (UpdateManager.isApkReadyAndValid(activity, remoteVc)) {
                UiUtils.showToast(activity, "Kurulum izni verildi! Güncelleme başlatılıyor...");
                UpdateManager.installApk(activity, apkFile);
            } else if (dlUrl != null && !dlUrl.isEmpty()) {
                UpdateManager.UpdateInfo info = new UpdateManager.UpdateInfo(
                        true, 0, "", remoteVc, remoteVn, "", "", dlUrl, 0);
                UiUtils.showToast(activity, "Kurulum izni verildi! İndirme başlatılıyor...");
                startDownloadFlow(activity, info, sScale);
            }
        }
    }

    public static void startDownloadFlow(final Activity activity, final UpdateManager.UpdateInfo info, final float sScale) {
        File existingFile = ApkProvider.getUpdateApkFile(activity);
        if (UpdateManager.isApkReadyAndValid(activity, info.remoteVersionCode)) {
            UiUtils.showToast(activity, "Güncelleme paketi hazır. Kurulum başlatılıyor...");
            UpdateManager.installApk(activity, existingFile);
            return;
        }

        final AlertDialog downloadDialog = new AlertDialog.Builder(activity).create();
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dpToPx(activity, 18);
        layout.setPadding(pad, pad, pad, pad);

        TextView dlTitle = new TextView(activity);
        dlTitle.setText("Saf Ekran v" + info.remoteVersionName + " İndiriliyor...");
        dlTitle.setTextColor(Color.WHITE);
        dlTitle.setTextSize(Math.round(13 * sScale));
        dlTitle.setTypeface(Typeface.DEFAULT_BOLD);
        dlTitle.setPadding(0, 0, 0, UiUtils.dpToPx(activity, 10));
        layout.addView(dlTitle);

        final ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setIndeterminate(false);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtils.dpToPx(activity, 18));
        pbLp.bottomMargin = UiUtils.dpToPx(activity, 8);
        progressBar.setLayoutParams(pbLp);
        layout.addView(progressBar);

        final TextView statusText = new TextView(activity);
        statusText.setText("Sunucuya bağlanılıyor...");
        statusText.setTextColor(UiUtils.COLOR_CYAN_LIGHT);
        statusText.setTextSize(Math.round(11 * sScale));
        statusText.setGravity(Gravity.CENTER);
        layout.addView(statusText);

        downloadDialog.setView(layout);
        downloadDialog.setCancelable(false);
        downloadDialog.show();

        UpdateManager.downloadUpdate(activity, info.downloadUrl, new UpdateManager.DownloadCallback() {
            @Override
            public void onProgress(final int percent, final long downloadedBytes, final long totalBytes) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setProgress(percent);
                        if (totalBytes > 0) {
                            String dlMb = String.format(java.util.Locale.US, "%.1f", downloadedBytes / (1024.0 * 1024.0));
                            String totMb = String.format(java.util.Locale.US, "%.1f", totalBytes / (1024.0 * 1024.0));
                            statusText.setText("%" + percent + " (" + dlMb + " / " + totMb + " MB)");
                        } else {
                            statusText.setText("%" + percent + " İndiriliyor...");
                        }
                    }
                });
            }

            @Override
            public void onCompleted(final File apkFile) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (downloadDialog.isShowing()) downloadDialog.dismiss();
                        UpdateManager.installApk(activity, apkFile);
                    }
                });
            }

            @Override
            public void onError(final String message) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (downloadDialog.isShowing()) downloadDialog.dismiss();
                        AlertDialog.Builder alert = new AlertDialog.Builder(activity);
                        alert.setTitle("⚠️ İndirme Başarısız Oldu");
                        alert.setMessage("Güncelleme paketi indirilirken hata oluştu:\n" + message);
                        alert.setPositiveButton("Tamam", null);
                        alert.show();
                    }
                });
            }
        });
    }
}
