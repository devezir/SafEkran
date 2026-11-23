package com.safekran.launcher.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.safekran.launcher.data.AppInfo;
import com.safekran.launcher.data.AppScanner;
import com.safekran.launcher.model.Tile;
import com.safekran.launcher.model.TileType;
import com.safekran.launcher.model.WorkspaceConfig;
import com.safekran.launcher.data.WorkspaceStorage;
import android.graphics.drawable.Drawable;
import com.safekran.launcher.util.IntentUtils;
import com.safekran.launcher.util.UiUtils;
import com.safekran.launcher.util.UpdateFlowManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Modular settings dialog managing the 7 accordion configuration sections,
 * live layout scaling, debounced UI scale adjustments, and system shortcuts.
 */
public final class LauncherSettingsDialog {

    public interface LauncherSettingsListener {
        void onRefreshViewsAndListeners();
        void onVirtualRemoteToggled(boolean enabled);
        void onOpenHiddenApps();
        void onOpenBackupRestore();
        void onResetToFactory();
    }

    private static AlertDialog activeDialog = null;
    private static final Handler scaleHandler = new Handler(Looper.getMainLooper());
    private static boolean isTrackingScaleTouch = false;

    private static class AccordionSection {
        Button header;
        LinearLayout content;
        boolean isExpanded;
    }

    private interface ValueCallback {
        void onValueChanged(float val);
    }

    private LauncherSettingsDialog() {}

    public static void show(final Activity activity,
                            final WorkspaceConfig config,
                            final WorkspaceView workspaceView,
                            final Map<String, Drawable> iconCache,
                            final LauncherSettingsListener listener) {
        show(activity, config, workspaceView, iconCache, listener, false);
    }

    public static void show(final Activity activity,
                            final WorkspaceConfig config,
                            final WorkspaceView workspaceView,
                            final Map<String, Drawable> iconCache,
                            final LauncherSettingsListener listener,
                            final boolean keepSliderFocus) {
        if (activeDialog != null) {
            if (activeDialog.isShowing()) {
                return;
            }
            try {
                activeDialog.setOnDismissListener(null);
                activeDialog.dismiss();
            } catch (Exception ignored) {}
            activeDialog = null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final float sScale = (config != null) ? config.settingsUiScale : 1.0f;

        ScrollView scroll = new ScrollView(activity);
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtils.dpToPx(activity, 16);
        layout.setPadding(pad, pad, pad, pad);

        // 1. İmza & Sürüm Başlığı
        TextView authorText = new TextView(activity);
        String currentVersion = "2.25.0";
        try {
            currentVersion = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        authorText.setText("Saf Ekran v" + currentVersion + " | Geliştirici: Vezir");
        authorText.setTextColor(UiUtils.COLOR_GREEN_SUCCESS);
        authorText.setTextSize(Math.round(12 * Math.min(1.4f, sScale)));
        authorText.setPadding(0, 0, 0, UiUtils.dpToPx(activity, 8));
        layout.addView(authorText);

        // 0. ARAYÜZ ÖLÇEĞİ (GÖZLÜK VE TV MENÜ BÜYÜTME - EN ÜSTTE)
        final int currentStep = Math.max(0, Math.min(33, Math.round((sScale * 100f - 85f) / 5f)));
        final int currentPct = 85 + currentStep * 5;

        LinearLayout scaleHeaderRow = new LinearLayout(activity);
        scaleHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        scaleHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        scaleHeaderRow.setPadding(0, UiUtils.dpToPx(activity, 4), 0, UiUtils.dpToPx(activity, 2));

        TextView scaleHeader = new TextView(activity);
        scaleHeader.setText("🔍 MENÜ ARAYÜZ ÖLÇEĞİ (GÖZLÜK & TV)");
        scaleHeader.setTextColor(UiUtils.COLOR_CYAN_LIGHT);
        scaleHeader.setTextSize(Math.round(11 * sScale));
        scaleHeader.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        scaleHeader.setLayoutParams(shLp);
        scaleHeaderRow.addView(scaleHeader);

        final TextView scaleValText = new TextView(activity);
        scaleValText.setText("%" + currentPct);
        scaleValText.setTextColor(UiUtils.COLOR_CYAN_ACCENT);
        scaleValText.setTextSize(Math.round(13 * sScale));
        scaleValText.setTypeface(Typeface.DEFAULT_BOLD);
        scaleValText.setGravity(Gravity.END);
        scaleHeaderRow.addView(scaleValText);

        layout.addView(scaleHeaderRow);

        // Slider ve [-] [+] Butonları Satırı (%85 - %250)
        final LinearLayout scaleRow = new LinearLayout(activity);
        scaleRow.setOrientation(LinearLayout.HORIZONTAL);
        scaleRow.setGravity(Gravity.CENTER_VERTICAL);
        scaleRow.setPadding(0, 0, 0, UiUtils.dpToPx(activity, 6));
        scaleRow.setClipChildren(false);
        scaleRow.setClipToPadding(false);

        int btnW = (int) (UiUtils.dpToPx(activity, 42) * Math.min(1.4f, sScale));
        int btnH = (int) (UiUtils.dpToPx(activity, 38) * Math.min(1.4f, sScale));

        final Button minusBtn = createStepperButton(activity, "－", sScale, btnW, btnH, 0, UiUtils.dpToPx(activity, 4));

        final SeekBar scaleSeekBar = new SeekBar(activity);
        scaleSeekBar.setMax(33);
        scaleSeekBar.setProgress(currentStep);
        scaleSeekBar.setKeyProgressIncrement(1);
        scaleSeekBar.setFocusable(true);
        scaleSeekBar.setFocusableInTouchMode(true);
        UiUtils.setupTvFocusSlider(scaleSeekBar);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sbLp.setMargins(UiUtils.dpToPx(activity, 4), 0, UiUtils.dpToPx(activity, 4), 0);
        scaleSeekBar.setLayoutParams(sbLp);

        final Button plusBtn = createStepperButton(activity, "＋", sScale, btnW, btnH, UiUtils.dpToPx(activity, 4), 0);

        scaleRow.addView(minusBtn);
        scaleRow.addView(scaleSeekBar);
        scaleRow.addView(plusBtn);
        layout.addView(scaleRow);

        scaleHandler.removeCallbacksAndMessages(null);
        final Runnable applyScaleRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTrackingScaleTouch) {
                    return;
                }
                int step = scaleSeekBar.getProgress();
                float newScale = (85 + step * 5) / 100f;
                if (Math.abs(config.settingsUiScale - newScale) > 0.01f) {
                    config.settingsUiScale = newScale;
                    WorkspaceStorage.saveWorkspace(activity, config);
                    if (activeDialog != null) {
                        activeDialog.setOnDismissListener(null);
                        try {
                            activeDialog.dismiss();
                        } catch (Exception ignored) {}
                        activeDialog = null;
                    }
                    show(activity, config, workspaceView, iconCache, listener, true);
                }
            }
        };

        scaleSeekBar.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        int cur = scaleSeekBar.getProgress();
                        if (cur < 33) {
                            scaleSeekBar.setProgress(cur + 1);
                            scaleHandler.removeCallbacks(applyScaleRunnable);
                            scaleHandler.postDelayed(applyScaleRunnable, 400);
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        int cur = scaleSeekBar.getProgress();
                        if (cur > 0) {
                            scaleSeekBar.setProgress(cur - 1);
                            scaleHandler.removeCallbacks(applyScaleRunnable);
                            scaleHandler.postDelayed(applyScaleRunnable, 400);
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        scaleSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int pct = 85 + progress * 5;
                scaleValText.setText("%" + pct);
                if (fromUser && !isTrackingScaleTouch) {
                    scaleHandler.removeCallbacks(applyScaleRunnable);
                    scaleHandler.postDelayed(applyScaleRunnable, 400);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                isTrackingScaleTouch = true;
                scaleHandler.removeCallbacks(applyScaleRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                isTrackingScaleTouch = false;
                scaleHandler.removeCallbacks(applyScaleRunnable);
                scaleHandler.postDelayed(applyScaleRunnable, 150);
            }
        });

        minusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int cur = scaleSeekBar.getProgress();
                if (cur > 0) {
                    scaleSeekBar.setProgress(cur - 1);
                    scaleHandler.removeCallbacks(applyScaleRunnable);
                    scaleHandler.postDelayed(applyScaleRunnable, 400);
                }
            }
        });

        plusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int cur = scaleSeekBar.getProgress();
                if (cur < 33) {
                    scaleSeekBar.setProgress(cur + 1);
                    scaleHandler.removeCallbacks(applyScaleRunnable);
                    scaleHandler.postDelayed(applyScaleRunnable, 400);
                }
            }
        });

        // --- VARSAYILAN BAŞLATICI KARTI ---
        final boolean isDefault = IntentUtils.isDefaultLauncher(activity);
        final Button defaultLauncherBtn = new Button(activity);
        if (isDefault) {
            defaultLauncherBtn.setText("✅ Saf Ekran Varsayılan Başlatıcı");
            defaultLauncherBtn.setTextColor(Color.parseColor("#A5D6A7"));
            UiUtils.setupTvFocusHighlight(defaultLauncherBtn, UiUtils.COLOR_GREEN_DARK, 4);
        } else {
            defaultLauncherBtn.setText("🏠 Saf Ekran'ı Varsayılan Başlatıcı Yap");
            defaultLauncherBtn.setTextColor(Color.WHITE);
            UiUtils.setupTvFocusHighlight(defaultLauncherBtn, UiUtils.COLOR_ORANGE, 4);
        }
        defaultLauncherBtn.setTextSize(Math.round(11 * Math.min(1.4f, sScale)));
        defaultLauncherBtn.setSingleLine(false);
        defaultLauncherBtn.setGravity(Gravity.CENTER);
        defaultLauncherBtn.setPadding(UiUtils.dpToPx(activity, 12), UiUtils.dpToPx(activity, 8),
                UiUtils.dpToPx(activity, 12), UiUtils.dpToPx(activity, 8));
        defaultLauncherBtn.setMinHeight((int) (UiUtils.dpToPx(activity, 42) * Math.min(1.4f, sScale)));
        LinearLayout.LayoutParams defLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        defLp.setMargins(0, UiUtils.dpToPx(activity, 4), 0, UiUtils.dpToPx(activity, 8));
        defaultLauncherBtn.setLayoutParams(defLp);
        defaultLauncherBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DefaultLauncherHubDialog.show(activity, sScale);
            }
        });
        layout.addView(defaultLauncherBtn);

        final List<AccordionSection> allSections = new ArrayList<AccordionSection>();

        // 1. Kutu Geometrisi (Canlı Önizleme — Varsayılan AÇIK)
        LinearLayout secGeometry = addAccordionSection(activity, layout, allSections, "1. KUTU GEOMETRİSİ (CANLI ÖNİZLEME)", true, sScale);
        addPercentSlider(activity, secGeometry, "Kutu Boyutu (Genişlik)", config.tileScale, 6, 20, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) {
                config.tileScale = val;
                workspaceView.requestLayout();
            }
        });
        addPercentSlider(activity, secGeometry, "Kutu Yükseklik Oranı (%75=TV, %100=Kare)", config.tileHeightRatio, 50, 100, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) {
                config.tileHeightRatio = val;
                workspaceView.requestLayout();
            }
        });
        addPercentSlider(activity, secGeometry, "Kutular Arası Boşluk (Gutter)", config.gutterPercent, 0, 5, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) {
                config.gutterPercent = val;
                workspaceView.requestLayout();
            }
        });

        // 2. Güvenli Alan (Overscan)
        LinearLayout secSafeArea = addAccordionSection(activity, layout, allSections, "2. GÜVENLİ ALAN (OVERSCAN)", false, sScale);
        addPercentSlider(activity, secSafeArea, "Üst Güvenli Alan", config.safeArea.top, 0, 20, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) { config.safeArea.top = val; workspaceView.requestLayout(); }
        });
        addPercentSlider(activity, secSafeArea, "Alt Güvenli Alan", config.safeArea.bottom, 0, 20, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) { config.safeArea.bottom = val; workspaceView.requestLayout(); }
        });
        addPercentSlider(activity, secSafeArea, "Sol Güvenli Alan", config.safeArea.left, 0, 20, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) { config.safeArea.left = val; workspaceView.requestLayout(); }
        });
        addPercentSlider(activity, secSafeArea, "Sağ Güvenli Alan", config.safeArea.right, 0, 20, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) { config.safeArea.right = val; workspaceView.requestLayout(); }
        });

        // 3. Odak ve Etkileşim
        LinearLayout secFocus = addAccordionSection(activity, layout, allSections, "3. ODAK VE ETKİLEŞİM", false, sScale);
        float currentFocusPercent = (config.focusScale - 1.0f);
        addPercentSlider(activity, secFocus, "Odaklanınca Büyüme Oranı", currentFocusPercent, 0, 15, sScale, new ValueCallback() {
            @Override public void onValueChanged(float val) { config.focusScale = 1.0f + val; }
        });

        // 4. Çerçeve ve Tema Stili
        LinearLayout secTheme = addAccordionSection(activity, layout, allSections, "4. ÇERÇEVE VE TEMA STİLİ", false, sScale);
        LinearLayout styleRow = new LinearLayout(activity);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        addStyleButton(activity, styleRow, "Sadece Odakta", "FOCUS_ONLY", sScale, config, workspaceView, listener);
        addStyleButton(activity, styleRow, "Her Zaman", "ALWAYS", sScale, config, workspaceView, listener);
        addStyleButton(activity, styleRow, "Yüzen", "FLOATING", sScale, config, workspaceView, listener);
        updateButtonGroupHighlight(styleRow, config.tileStyle);
        secTheme.addView(styleRow);

        LinearLayout themeRow = new LinearLayout(activity);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        addThemeButton(activity, themeRow, "Saf Siyah", "BLACK", sScale, config, workspaceView, listener);
        addThemeButton(activity, themeRow, "Koyu Gri", "DARK_GRAY", sScale, config, workspaceView, listener);
        addThemeButton(activity, themeRow, "Aydınlık", "LIGHT", sScale, config, workspaceView, listener);
        updateButtonGroupHighlight(themeRow, config.themeMode);
        secTheme.addView(themeRow);

        addAccordionCheckBox(activity, secTheme, "✨ Açılış Animasyonu (Windows Phone Süzülme)",
                UiUtils.COLOR_CYAN_LIGHT, config.animationEnabled, sScale, new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        config.animationEnabled = isChecked;
                        if (isChecked && workspaceView != null) {
                            workspaceView.playTurnstileAnimation();
                        }
                    }
                });

        // 5. Telefonda Kumanda Testi
        LinearLayout secRemote = addAccordionSection(activity, layout, allSections, "5. TELEFONDA KUMANDA TESTİ", false, sScale);
        addAccordionCheckBox(activity, secRemote, "🕹️ Sanal TV Kumandası (Ekranda Test Pedi)",
                UiUtils.COLOR_CYAN_LIGHT, config.virtualRemoteEnabled, sScale, new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        config.virtualRemoteEnabled = isChecked;
                        if (listener != null) listener.onVirtualRemoteToggled(isChecked);
                    }
                });

        // 6. Gizlenen ve Özel Kutular
        LinearLayout secHidden = addAccordionSection(activity, layout, allSections, "6. GİZLENEN VE ÖZEL KUTULAR", false, sScale);
        Button hiddenBtn = new Button(activity);
        hiddenBtn.setText("👁️ Gizlenen Uygulamaları ve Özel Kutuları Yönet");
        hiddenBtn.setTextSize(Math.round(11 * sScale));
        hiddenBtn.setTextColor(Color.WHITE);
        UiUtils.setupTvFocusHighlight(hiddenBtn, UiUtils.COLOR_TEAL_DARK, 4);
        hiddenBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onOpenHiddenApps();
            }
        });
        secHidden.addView(hiddenBtn);

        // 7. Düzen İşlemleri
        LinearLayout secLayoutOps = addAccordionSection(activity, layout, allSections, "7. DÜZEN İŞLEMLERİ", false, sScale);
        addAccordionCheckBox(activity, secLayoutOps, "🔒 Düzeni Kilitle (Taşıma ve Silmeyi Engelle)",
                UiUtils.COLOR_AMBER, config.isLayoutLocked, sScale, new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        config.isLayoutLocked = isChecked;
                    }
                });

        Button addSpacerBtn = new Button(activity);
        addSpacerBtn.setText("➕ Yeni Boşluk (Spacer) Ekle");
        addSpacerBtn.setTextSize(Math.round(11 * sScale));
        addSpacerBtn.setTextColor(Color.WHITE);
        UiUtils.setupTvFocusHighlight(addSpacerBtn, UiUtils.COLOR_FOCUS_DARK, 4);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spLp.bottomMargin = UiUtils.dpToPx(activity, 6);
        addSpacerBtn.setLayoutParams(spLp);
        addSpacerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Tile spacer = Tile.createDefault(TileType.SPACER);
                config.tiles.add(spacer);
                WorkspaceStorage.saveWorkspace(activity, config);
                workspaceView.rebuildViews();
                if (listener != null) listener.onRefreshViewsAndListeners();
                UiUtils.showToast(activity, "Yeni boşluk eklendi.");
            }
        });
        secLayoutOps.addView(addSpacerBtn);

        Button reflowBtn = new Button(activity);
        reflowBtn.setText("🔄 Sıralamayı Varsayılana Sıfırla (Alfabeye Göre Sırala)");
        reflowBtn.setTextSize(Math.round(11 * sScale));
        reflowBtn.setTextColor(Color.WHITE);
        UiUtils.setupTvFocusHighlight(reflowBtn, UiUtils.COLOR_FOCUS_DARK, 4);
        reflowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int cols = workspaceView.getCalculatedCols();
                List<AppInfo> apps = AppScanner.scanAllApps(activity);
                WorkspaceStorage.reflowContinuousTiles(config, apps, cols);
                WorkspaceStorage.saveWorkspace(activity, config);
                workspaceView.rebuildViews();
                if (listener != null) listener.onRefreshViewsAndListeners();
                UiUtils.showToast(activity, "Sıralama varsayılana sıfırlandı.");
            }
        });
        secLayoutOps.addView(reflowBtn);

        // Alt Eylem Butonları
        LinearLayout actionBlock = new LinearLayout(activity);
        actionBlock.setOrientation(LinearLayout.VERTICAL);
        actionBlock.setPadding(0, UiUtils.dpToPx(activity, 16), 0, UiUtils.dpToPx(activity, 8));

        Button saveBtn = new Button(activity);
        saveBtn.setText("💾 Ayarları Kaydet");
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setTextSize(Math.round(13 * Math.min(1.4f, sScale)));
        saveBtn.setTypeface(Typeface.DEFAULT_BOLD);
        saveBtn.setSingleLine(false);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setPadding(UiUtils.dpToPx(activity, 12), UiUtils.dpToPx(activity, 8),
                UiUtils.dpToPx(activity, 12), UiUtils.dpToPx(activity, 8));
        saveBtn.setMinHeight((int) (UiUtils.dpToPx(activity, 44) * Math.min(1.4f, sScale)));
        UiUtils.setupTvFocusHighlight(saveBtn, UiUtils.COLOR_GREEN_MEDIUM, 4);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        saveLp.bottomMargin = UiUtils.dpToPx(activity, 8);
        saveBtn.setLayoutParams(saveLp);

        LinearLayout subRow = new LinearLayout(activity);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.setWeightSum(2f);

        Button backupBtn = new Button(activity);
        backupBtn.setText("🔄 Yedek / Geri Yükle");
        backupBtn.setTextColor(Color.WHITE);
        backupBtn.setTextSize(Math.round(11 * Math.min(1.4f, sScale)));
        backupBtn.setSingleLine(false);
        backupBtn.setGravity(Gravity.CENTER);
        backupBtn.setPadding(UiUtils.dpToPx(activity, 6), UiUtils.dpToPx(activity, 6),
                UiUtils.dpToPx(activity, 6), UiUtils.dpToPx(activity, 6));
        backupBtn.setMinHeight((int) (UiUtils.dpToPx(activity, 40) * Math.min(1.4f, sScale)));
        UiUtils.setupTvFocusHighlight(backupBtn, UiUtils.COLOR_FOCUS_DARK, 4);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bLp.rightMargin = UiUtils.dpToPx(activity, 4);
        backupBtn.setLayoutParams(bLp);

        Button resetBtn = new Button(activity);
        resetBtn.setText("⚠️ Fabrika Ayarları");
        resetBtn.setTextColor(Color.WHITE);
        resetBtn.setTextSize(Math.round(11 * Math.min(1.4f, sScale)));
        resetBtn.setSingleLine(false);
        resetBtn.setGravity(Gravity.CENTER);
        resetBtn.setPadding(UiUtils.dpToPx(activity, 6), UiUtils.dpToPx(activity, 6),
                UiUtils.dpToPx(activity, 6), UiUtils.dpToPx(activity, 6));
        resetBtn.setMinHeight((int) (UiUtils.dpToPx(activity, 40) * Math.min(1.4f, sScale)));
        UiUtils.setupTvFocusHighlight(resetBtn, UiUtils.COLOR_RED_DARK, 4);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rLp.leftMargin = UiUtils.dpToPx(activity, 4);
        resetBtn.setLayoutParams(rLp);

        subRow.addView(backupBtn);
        subRow.addView(resetBtn);

        actionBlock.addView(saveBtn);
        actionBlock.addView(subRow);

        // En altta: Güncellemeleri Denetle Butonu
        Button checkUpdateBtn = new Button(activity);
        checkUpdateBtn.setText("🔄 Güncellemeleri Denetle");
        checkUpdateBtn.setTextSize(Math.round(11 * Math.min(1.4f, sScale)));
        checkUpdateBtn.setTextColor(UiUtils.COLOR_CYAN_LIGHT);
        checkUpdateBtn.setSingleLine(false);
        checkUpdateBtn.setGravity(Gravity.CENTER);
        checkUpdateBtn.setPadding(UiUtils.dpToPx(activity, 12), UiUtils.dpToPx(activity, 8),
                UiUtils.dpToPx(activity, 12), UiUtils.dpToPx(activity, 8));
        checkUpdateBtn.setMinHeight((int) (UiUtils.dpToPx(activity, 40) * Math.min(1.4f, sScale)));
        UiUtils.setupTvFocusHighlight(checkUpdateBtn, UiUtils.COLOR_CARD_DARK, 4);
        LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        upLp.topMargin = UiUtils.dpToPx(activity, 8);
        checkUpdateBtn.setLayoutParams(upLp);
        checkUpdateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UpdateFlowManager.startUpdateCheckFlow(activity, sScale);
            }
        });
        actionBlock.addView(checkUpdateBtn);

        layout.addView(actionBlock);

        scroll.addView(layout);
        builder.setView(scroll);

        activeDialog = builder.create();
        final AlertDialog dialog = activeDialog;

        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scaleHandler.removeCallbacksAndMessages(null);
                WorkspaceStorage.saveWorkspace(activity, config);
                workspaceView.rebuildViews();
                if (listener != null) listener.onRefreshViewsAndListeners();
                dialog.dismiss();
            }
        });

        backupBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scaleHandler.removeCallbacksAndMessages(null);
                dialog.dismiss();
                if (listener != null) listener.onOpenBackupRestore();
            }
        });

        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scaleHandler.removeCallbacksAndMessages(null);
                dialog.dismiss();
                if (listener != null) listener.onResetToFactory();
            }
        });

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                scaleHandler.removeCallbacksAndMessages(null);
                isTrackingScaleTouch = false;
                if (activeDialog == d) {
                    activeDialog = null;
                    if (listener != null) listener.onRefreshViewsAndListeners();
                }
            }
        });

        dialog.show();

        // Kenara yaslı ve canlı görüşü engellemeyen pencere ayarları
        Window window = dialog.getWindow();
        if (window != null) {
            boolean isLandscape = (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
            android.util.DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.dimAmount = 0.05f; // Saydama yakın
            if (isLandscape) {
                wlp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
                float widthFraction = (sScale >= 1.8f) ? 0.65f : ((sScale > 1.2f) ? 0.48f : 0.40f);
                wlp.width = (int) (dm.widthPixels * widthFraction);
                wlp.height = WindowManager.LayoutParams.MATCH_PARENT;
            } else {
                wlp.gravity = Gravity.BOTTOM;
                wlp.width = WindowManager.LayoutParams.MATCH_PARENT;
                float heightFraction = (sScale >= 1.8f) ? 0.95f : ((sScale > 1.2f) ? 0.85f : 0.70f);
                wlp.height = (int) (dm.heightPixels * heightFraction);
            }
            window.setAttributes(wlp);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#F0121212"));
            bg.setCornerRadius(UiUtils.dpToPx(activity, 8));
            window.setBackgroundDrawable(bg);
        }

        if (keepSliderFocus) {
            scaleSeekBar.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (scaleSeekBar != null) {
                        scaleSeekBar.setFocusable(true);
                        scaleSeekBar.setFocusableInTouchMode(true);
                        scaleSeekBar.requestFocus();
                    }
                }
            }, 50);
        }
    }

    public static void dismissCurrent() {
        if (activeDialog != null) {
            try {
                activeDialog.setOnDismissListener(null);
                activeDialog.dismiss();
            } catch (Exception ignored) {}
            activeDialog = null;
        }
        scaleHandler.removeCallbacksAndMessages(null);
        isTrackingScaleTouch = false;
    }

    private static LinearLayout addAccordionSection(Activity activity,
                                                    LinearLayout container,
                                                    final List<AccordionSection> allSections,
                                                    final String title,
                                                    boolean defaultExpanded,
                                                    float sScale) {
        final AccordionSection section = new AccordionSection();
        section.isExpanded = defaultExpanded;

        final Button header = new Button(activity);
        header.setText((defaultExpanded ? "▼ " : "► ") + title);
        header.setTextColor(UiUtils.COLOR_CYAN_LIGHT);
        header.setTextSize(Math.round(11 * sScale));
        header.setTypeface(Typeface.DEFAULT_BOLD);
        UiUtils.setupTvFocusHighlight(header, Color.parseColor("#263238"), 4);
        header.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        int padH = (int) (UiUtils.dpToPx(activity, 12) * Math.min(1.3f, sScale));
        int padV = (int) (UiUtils.dpToPx(activity, 8) * Math.min(1.3f, sScale));
        header.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hLp.topMargin = UiUtils.dpToPx(activity, 6);
        header.setLayoutParams(hLp);

        final LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(UiUtils.dpToPx(activity, 6), UiUtils.dpToPx(activity, 4),
                UiUtils.dpToPx(activity, 6), UiUtils.dpToPx(activity, 8));
        content.setVisibility(defaultExpanded ? View.VISIBLE : View.GONE);

        section.header = header;
        section.content = content;
        allSections.add(section);

        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean target = !section.isExpanded;
                for (AccordionSection s : allSections) {
                    s.isExpanded = false;
                    s.content.setVisibility(View.GONE);
                    String hText = s.header.getText().toString();
                    if (hText.startsWith("▼ ")) {
                        s.header.setText("► " + hText.substring(2));
                    }
                }
                section.isExpanded = target;
                section.content.setVisibility(target ? View.VISIBLE : View.GONE);
                header.setText((target ? "▼ " : "► ") + title);
            }
        });

        container.addView(header);
        container.addView(content);
        return content;
    }

    private static void addPercentSlider(Activity activity, LinearLayout layout, final String label,
                                         float initialVal, final int min, final int max,
                                         float sScale, final ValueCallback callback) {
        final TextView labelView = new TextView(activity);
        int percent = Math.round(initialVal * 100);
        labelView.setText(label + ": %" + percent);
        labelView.setTextColor(Color.parseColor("#CCCCCC"));
        labelView.setTextSize(Math.round(12 * sScale));
        labelView.setPadding(0, UiUtils.dpToPx(activity, 8), 0, UiUtils.dpToPx(activity, 2));
        layout.addView(labelView);

        SeekBar seekBar = new SeekBar(activity);
        UiUtils.setupTvFocusSlider(seekBar);
        seekBar.setMax(max - min);
        seekBar.setProgress(Math.max(0, percent - min));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int actualVal = min + progress;
                labelView.setText(label + ": %" + actualVal);
                callback.onValueChanged(actualVal / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        layout.addView(seekBar);
    }

    private static CheckBox addAccordionCheckBox(Activity activity, LinearLayout container,
                                                 String text, int textColor, boolean initialChecked,
                                                 float sScale, CompoundButton.OnCheckedChangeListener listener) {
        CheckBox cb = new CheckBox(activity);
        cb.setText(text);
        cb.setTextColor(textColor);
        cb.setTextSize(Math.round(12 * sScale));
        cb.setChecked(initialChecked);
        UiUtils.setupTvFocusCheckBox(cb);
        if (listener != null) cb.setOnCheckedChangeListener(listener);
        container.addView(cb);
        return cb;
    }

    private interface ConfigOptionCallback {
        void onSelect();
    }

    private static void addConfigOptionButton(final Activity activity, final LinearLayout row, String text,
                                              final String tag, float textSizeSp, float sScale,
                                              final ConfigOptionCallback callback, final String toastMsg) {
        Button btn = new Button(activity);
        btn.setText(text);
        btn.setTag(tag);
        btn.setTextSize(Math.round(textSizeSp * sScale));
        int padH = (int) (UiUtils.dpToPx(activity, 4) * Math.min(1.3f, sScale));
        int padV = (int) (UiUtils.dpToPx(activity, 6) * Math.min(1.3f, sScale));
        btn.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(UiUtils.dpToPx(activity, 2), UiUtils.dpToPx(activity, 2),
                UiUtils.dpToPx(activity, 2), UiUtils.dpToPx(activity, 4));
        btn.setLayoutParams(params);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.onSelect();
                updateButtonGroupHighlight(row, tag);
                UiUtils.showToast(activity, toastMsg);
            }
        });
        row.addView(btn);
    }

    private static void addStyleButton(final Activity activity, final LinearLayout row, String text,
                                       final String style, float sScale,
                                       final WorkspaceConfig config, final WorkspaceView workspaceView,
                                       final LauncherSettingsListener listener) {
        addConfigOptionButton(activity, row, text, style, 10f, sScale, new ConfigOptionCallback() {
            @Override
            public void onSelect() {
                config.tileStyle = style;
                workspaceView.rebuildViews();
                if (listener != null) listener.onRefreshViewsAndListeners();
            }
        }, "Çerçeve Stili: " + style);
    }

    private static void addThemeButton(final Activity activity, final LinearLayout row, String text,
                                       final String mode, float sScale,
                                       final WorkspaceConfig config, final WorkspaceView workspaceView,
                                       final LauncherSettingsListener listener) {
        addConfigOptionButton(activity, row, text, mode, 11f, sScale, new ConfigOptionCallback() {
            @Override
            public void onSelect() {
                config.themeMode = mode;
                workspaceView.rebuildViews();
                if (listener != null) listener.onRefreshViewsAndListeners();
            }
        }, "Tema: " + mode);
    }

    private static void updateButtonGroupHighlight(LinearLayout row, String selectedTag) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof Button) {
                Button b = (Button) child;
                boolean isSelected = selectedTag != null && selectedTag.equals(b.getTag());
                UiUtils.setupTvFocusToggleButton(b, isSelected, 4);
            }
        }
    }

    private static Button createStepperButton(Activity activity, String text, float sScale, int btnW, int btnH, int leftMargin, int rightMargin) {
        Button btn = new Button(activity);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, 0, 0, 0);
        btn.setIncludeFontPadding(false);
        btn.setMinHeight(0);
        btn.setMinimumWidth(0);
        btn.setTextSize(Math.round(16 * Math.min(1.4f, sScale)));
        UiUtils.setupTvFocusHighlight(btn, UiUtils.COLOR_FOCUS_DARK, 4);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnW, btnH);
        lp.leftMargin = leftMargin;
        lp.rightMargin = rightMargin;
        btn.setLayoutParams(lp);
        return btn;
    }
}
