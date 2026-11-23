package com.safekran.launcher.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.safekran.launcher.util.IntentUtils;
import com.safekran.launcher.util.UiUtils;

/**
 * Universal Hub for setting Saf Ekran as default launcher across all Android versions (Android 2.2 - 17).
 */
public final class DefaultLauncherHubDialog {

    private DefaultLauncherHubDialog() {}

    public static void show(final Activity activity, final float sScale) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dpToPx(activity, 16);
        layout.setPadding(pad, pad, pad, pad);

        TextView titleView = new TextView(activity);
        titleView.setText("🏠 Varsayılan Başlatıcı Kurulum Merkezi");
        titleView.setTextSize(Math.round(15 * sScale));
        titleView.setTextColor(UiUtils.COLOR_CYAN_LIGHT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setPadding(0, 0, 0, UiUtils.dpToPx(activity, 6));
        layout.addView(titleView);

        TextView descView = new TextView(activity);
        descView.setText("Cihazınızın modeline ve Android sürümüne göre aşağıdaki yöntemlerden birini seçebilirsiniz:");
        descView.setTextSize(Math.round(11 * sScale));
        descView.setTextColor(UiUtils.COLOR_TEXT_LIGHT_GRAY);
        descView.setPadding(0, 0, 0, UiUtils.dpToPx(activity, 12));
        layout.addView(descView);

        // 1. Otomatik Sistem Seçicisi (Tüm Cihazlar & TV)
        addHubOption(layout, activity, "🚀 1. Otomatik Sistem Seçicisi (Tüm Cihazlar & TV)",
                "Evrensel Android seçicisini açar (Android 2.2 - 17)",
                UiUtils.COLOR_BLUE_HEADER, sScale, new Runnable() {
                    @Override public void run() { IntentUtils.triggerHomeChooser(activity); }
                });

        // 2. Android TV & Google TV Özel Çözümü (Önerilen)
        addHubOption(layout, activity, "📺 2. Android TV & Google TV Çözümü (Önerilen)",
                "Kilitli TV'lerde mevcut başlatıcının varsayılanını temizleme sayfası",
                UiUtils.COLOR_TEAL, sScale, new Runnable() {
                    @Override public void run() { IntentUtils.openCurrentLauncherDetails(activity); }
                });

        // 3. Google Resmi Onay Penceresi (Android 10 - 17)
        addHubOption(layout, activity, "📱 3. Google Resmi Onay Penceresi (RoleManager)",
                "Mobil telefonlar ve AOSP tabletler için resmi onay penceresi",
                UiUtils.COLOR_PURPLE, sScale, new Runnable() {
                    @Override public void run() { IntentUtils.triggerRoleManagerHome(activity); }
                });

        // 4. Sistem Ana Ekran Ayarları Sayfası
        addHubOption(layout, activity, "⚙️ 4. Sistem Ana Ekran Ayarları Sayfası",
                "Cihazın kendi Ayarlar -> Varsayılan Uygulamalar / Ana Ekran menüsü",
                UiUtils.COLOR_SLATE, sScale, new Runnable() {
                    @Override public void run() { IntentUtils.openHomeSettings(activity); }
                });

        // 5. Xiaomi / HyperOS / MIUI Özel Sayfası
        addHubOption(layout, activity, "🟠 5. Xiaomi / HyperOS / MIUI Özel Sayfası",
                "Xiaomi cihazların gizlediği 'Varsayılan Başlatıcı' ayarını açar",
                UiUtils.COLOR_ORANGE, sScale, new Runnable() {
                    @Override public void run() { IntentUtils.openXiaomiDefaultAppsSettings(activity); }
                });

        // Alt Bilgi Kartı
        TextView tipView = new TextView(activity);
        tipView.setText("💡 İpucu: Kumandanızdaki HOME tuşuna bastığınızda ekrana liste gelirse 'Saf Ekran'ı ve ardından 'Her Zaman'ı seçiniz.");
        tipView.setTextSize(Math.round(11 * sScale));
        tipView.setTextColor(UiUtils.COLOR_GREEN_SUCCESS);
        tipView.setPadding(0, UiUtils.dpToPx(activity, 10), 0, UiUtils.dpToPx(activity, 8));
        layout.addView(tipView);

        scroll.addView(layout);
        builder.setView(scroll);
        builder.setNegativeButton("Kapat", null);
        AlertDialog d = builder.create();
        d.show();
    }

    private static void addHubOption(LinearLayout layout, Activity activity,
                                     String title, String subtitle, int bgColor, float sScale,
                                     final Runnable action) {
        Button btn = UiUtils.createOptionButton(activity, title, subtitle, bgColor, sScale);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (action != null) action.run();
            }
        });
        layout.addView(btn);
    }
}
