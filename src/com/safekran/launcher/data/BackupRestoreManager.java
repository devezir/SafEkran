package com.safekran.launcher.data;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.safekran.launcher.model.WorkspaceConfig;
import com.safekran.launcher.util.UiUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * Saf Ekran - Çalışma Alanı Yedekleme ve Geri Yükleme Yöneticisi
 * İndirilenler klasörüne JSON dışa aktarma, içe aktarma ve ham metin editörünü yönetir.
 */
public final class BackupRestoreManager {

    public interface OnConfigRestoredCallback {
        void onConfigRestored(WorkspaceConfig newConfig);
    }

    private BackupRestoreManager() {
        // Yardımcı sınıf, örneklenemez
    }

    public static File getDownloadsDir(Activity activity) {
        return activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
    }

    public static void openBackupRestoreDialog(final Activity activity, final WorkspaceConfig config, final OnConfigRestoredCallback callback) {
        if (activity == null || activity.isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("🔄 Çalışma Alanı Yedekleme & Geri Yükleme");

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padH = UiUtils.dpToPx(activity, 20);
        int padV = UiUtils.dpToPx(activity, 16);
        layout.setPadding(padH, padV, padH, UiUtils.dpToPx(activity, 12));

        TextView info = new TextView(activity);
        info.setText("Yedekler cihazınızın Download (İndirilenler) klasöründe tarih ve saat etiketiyle saklanır.");
        info.setTextColor(Color.parseColor("#B0BEC5"));
        info.setTextSize(12);
        info.setPadding(0, 0, 0, UiUtils.dpToPx(activity, 14));
        layout.addView(info);

        // 1. Download Klasörüne Yedekle Butonu
        Button btnSaveDownload = new Button(activity);
        btnSaveDownload.setText("📥 Download Klasörüne Yedekle (Tarihli)");
        btnSaveDownload.setTextColor(Color.WHITE);
        btnSaveDownload.setTypeface(Typeface.DEFAULT_BOLD);
        btnSaveDownload.setBackgroundColor(Color.parseColor("#1B5E20"));
        btnSaveDownload.setTextSize(12);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtils.dpToPx(activity, 44));
        p1.bottomMargin = UiUtils.dpToPx(activity, 8);
        btnSaveDownload.setLayoutParams(p1);
        layout.addView(btnSaveDownload);

        // 2. Download Klasöründen Seç ve Geri Yükle Butonu
        Button btnRestoreDownload = new Button(activity);
        btnRestoreDownload.setText("📂 Download'dan Seç ve Geri Yükle");
        btnRestoreDownload.setTextColor(Color.WHITE);
        btnRestoreDownload.setTypeface(Typeface.DEFAULT_BOLD);
        btnRestoreDownload.setBackgroundColor(Color.parseColor("#0D47A1"));
        btnRestoreDownload.setTextSize(12);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtils.dpToPx(activity, 44));
        p2.bottomMargin = UiUtils.dpToPx(activity, 8);
        btnRestoreDownload.setLayoutParams(p2);
        layout.addView(btnRestoreDownload);

        // 3. Panodan / Metinle Düzenleme (B Planı)
        Button btnRawJson = new Button(activity);
        btnRawJson.setText("📋 Panodan / Metin ile Yedekle");
        btnRawJson.setTextColor(Color.parseColor("#ECEFF1"));
        btnRawJson.setBackgroundColor(Color.parseColor("#37474F"));
        btnRawJson.setTextSize(11);
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiUtils.dpToPx(activity, 38));
        btnRawJson.setLayoutParams(p3);
        layout.addView(btnRawJson);

        builder.setView(layout);
        builder.setNegativeButton("Kapat", null);
        final AlertDialog dialog = builder.create();

        btnSaveDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                saveBackupToDownloads(activity, config);
            }
        });

        btnRestoreDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                openFileRestoreChooserDialog(activity, callback);
            }
        });

        btnRawJson.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                openRawJsonDialog(activity, config, callback);
            }
        });

        dialog.show();
    }

    public static void saveBackupToDownloads(Activity activity, WorkspaceConfig config) {
        try {
            File dir = getDownloadsDir(activity);
            if (dir == null) {
                UiUtils.showToastLong(activity, "Download klasörüne erişilemedi!");
                return;
            }
            String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date());
            String fileName = "SafEkran_Yedek_" + timeStamp + ".json";
            File backupFile = new File(dir, fileName);

            String jsonStr = WorkspaceStorage.exportToJsonString(config);
            writeStringToFile(backupFile, jsonStr);

            UiUtils.showToastLong(activity, "✅ Yedek kaydedildi:\n" + fileName);
        } catch (Exception e) {
            UiUtils.showToastLong(activity, "Yedek kaydedilemedi: " + e.getMessage());
        }
    }

    public static void openFileRestoreChooserDialog(final Activity activity, final OnConfigRestoredCallback callback) {
        File dir = getDownloadsDir(activity);
        if (dir == null || !dir.exists()) {
            UiUtils.showToastLong(activity, "Download klasörü bulunamadı!");
            return;
        }

        File[] files = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File f) {
                if (!f.isFile()) return false;
                String name = f.getName().toLowerCase(Locale.US);
                return name.startsWith("safekran_yedek") && name.endsWith(".json");
            }
        });

        if (files == null || files.length == 0) {
            files = dir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    if (!f.isFile()) return false;
                    String name = f.getName().toLowerCase(Locale.US);
                    return name.endsWith(".json") && name.contains("safekran");
                }
            });
        }

        if (files == null || files.length == 0) {
            AlertDialog.Builder noFilesBuilder = new AlertDialog.Builder(activity);
            noFilesBuilder.setTitle("Yedek Dosyası Bulunamadı");
            noFilesBuilder.setMessage("Download klasöründe herhangi bir SafEkran yedek dosyası (*.json) bulunamadı.\n\nKlasör: " + dir.getAbsolutePath());
            noFilesBuilder.setPositiveButton("Tamam", null);
            noFilesBuilder.show();
            return;
        }

        // En yeni dosyadan en eskiye sırala
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return Long.compare(f2.lastModified(), f1.lastModified());
            }
        });

        final File[] sortedFiles = files;
        String[] displayItems = new String[sortedFiles.length];
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

        for (int i = 0; i < sortedFiles.length; i++) {
            File f = sortedFiles[i];
            long sizeKb = Math.max(1, f.length() / 1024);
            String dateFormatted = sdf.format(new Date(f.lastModified()));
            displayItems[i] = "📄 " + f.getName() + "\n     " + dateFormatted + "  (" + sizeKb + " KB)";
        }

        AlertDialog.Builder chooser = new AlertDialog.Builder(activity);
        chooser.setTitle("📂 Geri Yüklenecek Yedeği Seçin (" + sortedFiles.length + " dosya)");
        chooser.setItems(displayItems, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                File selected = sortedFiles[which];
                restoreFromFile(activity, selected, callback);
            }
        });
        chooser.setNegativeButton("İptal", null);
        chooser.show();
    }

    public static void restoreFromFile(Activity activity, File file, OnConfigRestoredCallback callback) {
        try {
            String jsonStr = readFileToString(file);
            WorkspaceConfig imported = WorkspaceStorage.importFromJsonString(jsonStr);
            if (imported != null) {
                WorkspaceStorage.saveWorkspace(activity, imported);
                if (callback != null) {
                    callback.onConfigRestored(imported);
                }
                UiUtils.showToastLong(activity, "✅ " + file.getName() + "\nbaşarıyla geri yüklendi!");
            } else {
                UiUtils.showToastLong(activity, "❌ Geçersiz yedek dosyası içeriği!");
            }
        } catch (Exception e) {
            UiUtils.showToastLong(activity, "Yedek yüklenemedi: " + e.getMessage());
        }
    }

    public static void openRawJsonDialog(final Activity activity, final WorkspaceConfig config, final OnConfigRestoredCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("📋 JSON Metin ile Yedekleme / Düzenleme");

        final EditText input = new EditText(activity);
        input.setText(WorkspaceStorage.exportToJsonString(config));
        input.setSelectAllOnFocus(true);
        input.setTextSize(11);
        builder.setView(input);

        builder.setPositiveButton("Geri Yükle", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String jsonStr = input.getText().toString();
                WorkspaceConfig imported = WorkspaceStorage.importFromJsonString(jsonStr);
                if (imported != null) {
                    WorkspaceStorage.saveWorkspace(activity, imported);
                    if (callback != null) {
                        callback.onConfigRestored(imported);
                    }
                    UiUtils.showToastLong(activity, "Yedek başarıyla geri yüklendi!");
                } else {
                    UiUtils.showToastLong(activity, "Geçersiz JSON verisi!");
                }
            }
        });

        builder.setNegativeButton("Kapat", null);
        builder.show();
    }

    public static String readFileToString(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        fis.close();
        return sb.toString();
    }

    public static void writeStringToFile(File file, String content) throws Exception {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(content.getBytes("UTF-8"));
        fos.flush();
        fos.close();
    }
}
