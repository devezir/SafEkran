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
        Button b1 = createOptionButton(activity,
                "🚀 1. Otomatik Sistem Seçicisi (Tüm Cihazlar & TV)",
                "Evrensel Android seçicisini açar (Android 2.2 - 17)",
                Color.parseColor("#0288D1"), sScale);
        b1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                IntentUtils.triggerHomeChooser(activity);
            }
        });
        layout.addView(b1);

        // 2. Android TV & Google TV Özel Çözümü (Önerilen)
        Button b2 = createOptionButton(activity,
                "📺 2. Android TV & Google TV Çözümü (Önerilen)",
                "Kilitli TV'lerde mevcut başlatıcının varsayılanını temizleme sayfası",
                Color.parseColor("#00897B"), sScale);
        b2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                IntentUtils.openCurrentLauncherDetails(activity);
            }
        });
        layout.addView(b2);

        // 3. Google Resmi Onay Penceresi (Android 10 - 17)
        Button b3 = createOptionButton(activity,
                "📱 3. Google Resmi Onay Penceresi (RoleManager)",
                "Mobil telefonlar ve AOSP tabletler için resmi onay penceresi",
                Color.parseColor("#5E35B1"), sScale);
        b3.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                IntentUtils.triggerRoleManagerHome(activity);
            }
        });
        layout.addView(b3);

        // 4. Sistem Ana Ekran Ayarları Sayfası
        Button b4 = createOptionButton(activity,
                "⚙️ 4. Sistem Ana Ekran Ayarları Sayfası",
                "Cihazın kendi Ayarlar -> Varsayılan Uygulamalar / Ana Ekran menüsü",
                Color.parseColor("#455A64"), sScale);
        b4.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                IntentUtils.openHomeSettings(activity);
            }
        });
        layout.addView(b4);

        // 5. Xiaomi / HyperOS / MIUI Özel Sayfası
        Button b5 = createOptionButton(activity,
                "🟠 5. Xiaomi / HyperOS / MIUI Özel Sayfası",
                "Xiaomi cihazların gizlediği 'Varsayılan Başlatıcı' ayarını açar",
                Color.parseColor("#E65100"), sScale);
        b5.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                IntentUtils.openXiaomiDefaultAppsSettings(activity);
            }
        });
        layout.addView(b5);

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

    private static Button createOptionButton(Activity activity, String title, String subtitle, int normalBgColor, float sScale) {
        Button btn = new Button(activity);
        btn.setText(title + "\n" + subtitle);
        btn.setTextSize(Math.round(11 * sScale));
        btn.setTextColor(Color.WHITE);
        UiUtils.setupTvFocusHighlight(btn, normalBgColor, 4);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, UiUtils.dpToPx(activity, 4), 0, UiUtils.dpToPx(activity, 6));
        btn.setLayoutParams(lp);
        btn.setPadding(UiUtils.dpToPx(activity, 12), UiUtils.dpToPx(activity, 10),
                UiUtils.dpToPx(activity, 12), UiUtils.dpToPx(activity, 10));
        btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return btn;
    }
}
