package com.safekran.launcher.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import com.safekran.launcher.data.AppInfo;
import com.safekran.launcher.data.AppScanner;
import com.safekran.launcher.model.Tile;
import com.safekran.launcher.model.TileType;
import com.safekran.launcher.model.WorkspaceConfig;
import android.graphics.drawable.Drawable;
import com.safekran.launcher.util.UiUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for managing tile context menus, hidden/missing app recovery,
 * spacer modifications, and colored tile action assignments.
 */
public final class TileContextMenuHelper {

    public interface TileContextMenuListener {
        void onSaveAndRefresh(String message);
        void onStartTileMove(Tile tile);
        void onOpenClockApp();
        void onOpenLauncherSettings();
    }

    private TileContextMenuHelper() {}

    public static List<AppInfo> getHiddenOrMissingApps(Activity activity, WorkspaceConfig config) {
        List<AppInfo> allApps = AppScanner.scanAllApps(activity);
        Map<String, Boolean> visiblePackages = new HashMap<String, Boolean>();
        if (config != null && config.tiles != null) {
            for (Tile t : config.tiles) {
                if (t.type == TileType.APP && t.packageName != null) {
                    visiblePackages.put(t.packageName, true);
                }
            }
        }
        List<AppInfo> hiddenApps = new ArrayList<AppInfo>();
        for (AppInfo ai : allApps) {
            if (!visiblePackages.containsKey(ai.packageName)) {
                hiddenApps.add(ai);
            }
        }
        return hiddenApps;
    }

    public static List<Tile> getMissingSpecialTiles(WorkspaceConfig config) {
        if (config == null) return new ArrayList<Tile>();
        return config.getMissingSpecialTiles();
    }

    public static void openHiddenAppsDialog(final Activity activity,
                                           final WorkspaceConfig config,
                                           final Map<String, Drawable> iconCache,
                                           final Tile anchorTile,
                                           final TileContextMenuListener listener) {
        final List<AppInfo> hidden = getHiddenOrMissingApps(activity, config);
        final List<Tile> missingSpecials = getMissingSpecialTiles(config);

        if (hidden.isEmpty() && missingSpecials.isEmpty()) {
            UiUtils.showToast(activity, "Gizlenmiş kutu veya uygulama yok. Hepsi ana ekranda mevcut.");
            return;
        }

        final boolean hasMultipleApps = hidden.size() > 1;
        int totalItems = (hasMultipleApps ? 1 : 0) + missingSpecials.size() + hidden.size();
        final String[] items = new String[totalItems];

        int idx = 0;
        if (hasMultipleApps) {
            items[idx++] = "➕ TÜM GİZLENEN UYGULAMALARI GERİ GETİR (" + hidden.size() + ")";
        }

        for (int i = 0; i < missingSpecials.size(); i++) {
            Tile st = missingSpecials.get(i);
            String prefix = st.isColorTile() ? "[RENK KUTUSU] " : "[ÖZEL KUTU] ";
            items[idx++] = prefix + st.label + " Kutusunu Ekle";
        }

        for (int i = 0; i < hidden.size(); i++) {
            items[idx++] = hidden.get(i).label;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(anchorTile != null ? anchorTile.label + " Yanına Kutu Ekle" : "Gizlenen Kutuları / Uygulamaları Ekle");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int curIdx = 0;
                if (hasMultipleApps) {
                    if (which == curIdx) {
                        for (AppInfo ai : hidden) {
                            Tile nt = new Tile(TileType.APP, ai.label, 0, 0, 1, 1);
                            nt.packageName = ai.packageName;
                            nt.activityName = ai.activityName;
                            if (iconCache != null) {
                                iconCache.put(ai.packageName, ai.icon);
                            }
                            config.tiles.add(nt);
                        }
                        if (listener != null) {
                            listener.onSaveAndRefresh("Tüm gizlenen uygulamalar geri eklendi.");
                        }
                        return;
                    }
                    curIdx++;
                }

                if (which < curIdx + missingSpecials.size()) {
                    int specialIdx = which - curIdx;
                    Tile st = missingSpecials.get(specialIdx);
                    insertTileAt(config, st, anchorTile);
                    if (listener != null) {
                        listener.onSaveAndRefresh(st.label + " kutusu eklendi.");
                    }
                    return;
                }
                curIdx += missingSpecials.size();

                int appIdx = which - curIdx;
                AppInfo selected = hidden.get(appIdx);
                Tile newTile = new Tile(TileType.APP, selected.label, 0, 0, 1, 1);
                newTile.packageName = selected.packageName;
                newTile.activityName = selected.activityName;
                if (iconCache != null) {
                    iconCache.put(selected.packageName, selected.icon);
                }
                insertTileAt(config, newTile, anchorTile);
                if (listener != null) {
                    listener.onSaveAndRefresh(selected.label + " ana ekrana eklendi.");
                }
            }
        });
        builder.show();
    }

    public static void openColorAssignmentDialog(final Activity activity,
                                                 final WorkspaceConfig config,
                                                 final Map<String, Drawable> iconCache,
                                                 final Tile tile,
                                                 final TileContextMenuListener listener) {
        final String prefix = tile.getColorPrefix();

        String[] actions = new String[]{
                "📱 Bir Uygulama Ata (YouTube, Netflix vb.)...",
                "👁️ Gizlenen Uygulamalar Menüsü",
                "🌗 Hızlı Tema Değiştir (Siyah ↔ Aydınlık)",
                "⚙️ Başlatıcı Ayarları",
                "🔧 Android Sistem Ayarları",
                "🚫 İşlevi Temizle (Boş)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(tile.label + " — İşlem / Uygulama Ata");
        builder.setItems(actions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    final List<AppInfo> apps = AppScanner.scanAllApps(activity);
                    String[] appNames = new String[apps.size()];
                    for (int i = 0; i < apps.size(); i++) {
                        appNames[i] = apps.get(i).label;
                    }
                    AlertDialog.Builder appDialog = new AlertDialog.Builder(activity);
                    appDialog.setTitle(tile.label + " için Uygulama Seç");
                    appDialog.setItems(appNames, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int appIdx) {
                            AppInfo chosen = apps.get(appIdx);
                            tile.actionType = "APP";
                            tile.packageName = chosen.packageName;
                            tile.activityName = chosen.activityName;
                            tile.label = prefix + chosen.label;
                            if (iconCache != null) {
                                iconCache.put(chosen.packageName, chosen.icon);
                            }
                            if (listener != null) {
                                listener.onSaveAndRefresh(tile.label + " atandı.");
                            }
                        }
                    });
                    appDialog.show();
                } else if (which == 1) {
                    tile.actionType = "HIDDEN_APPS";
                    tile.packageName = "";
                    tile.label = prefix + "Gizlenenler";
                    if (listener != null) {
                        listener.onSaveAndRefresh(tile.label + " güncellendi.");
                    }
                } else if (which == 2) {
                    tile.actionType = "THEME_TOGGLE";
                    tile.packageName = "";
                    tile.label = prefix + "Tema";
                    if (listener != null) {
                        listener.onSaveAndRefresh(tile.label + " güncellendi.");
                    }
                } else if (which == 3) {
                    tile.actionType = "LAUNCHER_SETTINGS";
                    tile.packageName = "";
                    tile.label = prefix + "Ayarlar";
                    if (listener != null) {
                        listener.onSaveAndRefresh(tile.label + " güncellendi.");
                    }
                } else if (which == 4) {
                    tile.actionType = "SYSTEM_SETTINGS";
                    tile.packageName = "";
                    tile.label = prefix + "Sistem";
                    if (listener != null) {
                        listener.onSaveAndRefresh(tile.label + " güncellendi.");
                    }
                } else if (which == 5) {
                    tile.actionType = "NONE";
                    tile.packageName = "";
                    String cName = tile.getColorName();
                    tile.label = prefix + cName;
                    if (listener != null) {
                        listener.onSaveAndRefresh(tile.label + " güncellendi.");
                    }
                }
            }
        });
        builder.show();
    }

    public static void openTileContextMenu(final Activity activity,
                                           final WorkspaceConfig config,
                                           final Map<String, Drawable> iconCache,
                                           final Tile tile,
                                           final int calculatedCols,
                                           final TileContextMenuListener listener) {
        if (config != null && config.isLayoutLocked) {
            UiUtils.showToast(activity, "🔒 Düzen kilitli! Kutu taşımak veya düzenlemek için Ayarlar'dan kilidi açın.");
            return;
        }

        if (tile.type == TileType.SPACER) {
            List<String> spacerOptions = new ArrayList<String>();
            spacerOptions.add("➡️ Genişlet (+1 Sütun)");
            spacerOptions.add("⬅️ Daralt (-1 Sütun)");
            spacerOptions.add("⬆️ Yükselt (+1 Satır)");
            spacerOptions.add("⬇️ Alçalt (-1 Satır)");
            spacerOptions.add("🔁 Konumunu Değiştir (Taşı)");
            spacerOptions.add("🗑️ Boşluğu Kaldır (Sil)");

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("Boşluk (" + tile.colSpan + "x" + tile.rowSpan + ") — Düzenle");
            builder.setItems(spacerOptions.toArray(new String[0]), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        tile.colSpan = Math.min(calculatedCols, tile.colSpan + 1);
                        if (listener != null) listener.onSaveAndRefresh("Boşluk genişletildi (" + tile.colSpan + " sütun).");
                    } else if (which == 1) {
                        tile.colSpan = Math.max(1, tile.colSpan - 1);
                        if (listener != null) listener.onSaveAndRefresh("Boşluk daraltıldı (" + tile.colSpan + " sütun).");
                    } else if (which == 2) {
                        tile.rowSpan = Math.min(4, tile.rowSpan + 1);
                        if (listener != null) listener.onSaveAndRefresh("Boşluk yükseltildi (" + tile.rowSpan + " satır).");
                    } else if (which == 3) {
                        tile.rowSpan = Math.max(1, tile.rowSpan - 1);
                        if (listener != null) listener.onSaveAndRefresh("Boşluk alçaltıldı (" + tile.rowSpan + " satır).");
                    } else if (which == 4) {
                        if (listener != null) listener.onStartTileMove(tile);
                    } else if (which == 5) {
                        config.tiles.remove(tile);
                        if (listener != null) listener.onSaveAndRefresh("Boşluk kaldırıldı.");
                    }
                }
            });
            builder.show();
            return;
        }

        final boolean isColorTile = tile.isColorTile();
        final boolean isClockTile = (tile.type == TileType.CLOCK);

        final List<AppInfo> hiddenApps = getHiddenOrMissingApps(activity, config);
        String addAppOption = hiddenApps.isEmpty() ? "➕ Sağına Uygulama Ekle (Gizlenen yok)" : "➕ Sağına Uygulama Ekle (" + hiddenApps.size() + " gizlenen)";

        List<String> optionsList = new ArrayList<String>();
        if (isClockTile) {
            optionsList.add("⏰ Sistem Alarm / Saat Uygulamasını Aç");
        }
        if (isColorTile) {
            optionsList.add("🎯 İşlem / Uygulama Ata");
        }
        optionsList.add("🔁 Konumunu Değiştir (Taşı)");
        optionsList.add("➡️ Genişlet (+1 Sütun)");
        optionsList.add("⬅️ Daralt (-1 Sütun)");
        optionsList.add("⬆️ Yükselt (+1 Satır)");
        optionsList.add("⬇️ Alçalt (-1 Satır)");
        optionsList.add("🔲 Sağına Boşluk Ekle");
        optionsList.add(addAppOption);
        optionsList.add("🗑️ Kutuyu Kaldır (Gizle)");
        optionsList.add("⚙️ Başlatıcı Ayarları");

        String[] options = optionsList.toArray(new String[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(tile.label + " — Kutu İşlemleri");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int shift = 0;
                if (isClockTile) {
                    if (which == 0) {
                        if (listener != null) listener.onOpenClockApp();
                        return;
                    }
                    shift++;
                }
                if (isColorTile) {
                    if (which == shift) {
                        openColorAssignmentDialog(activity, config, iconCache, tile, listener);
                        return;
                    }
                    shift++;
                }

                int effectiveWhich = which - shift;
                if (effectiveWhich == 0) {
                    if (listener != null) listener.onStartTileMove(tile);
                } else if (effectiveWhich == 1) {
                    tile.colSpan = Math.min(calculatedCols, tile.colSpan + 1);
                    if (listener != null) listener.onSaveAndRefresh(tile.label + " genişletildi (" + tile.colSpan + " sütun).");
                } else if (effectiveWhich == 2) {
                    tile.colSpan = Math.max(1, tile.colSpan - 1);
                    if (listener != null) listener.onSaveAndRefresh(tile.label + " daraltıldı (" + tile.colSpan + " sütun).");
                } else if (effectiveWhich == 3) {
                    tile.rowSpan = Math.min(4, tile.rowSpan + 1);
                    if (listener != null) listener.onSaveAndRefresh(tile.label + " yükseltildi (" + tile.rowSpan + " satır).");
                } else if (effectiveWhich == 4) {
                    tile.rowSpan = Math.max(1, tile.rowSpan - 1);
                    if (listener != null) listener.onSaveAndRefresh(tile.label + " alçaltıldı (" + tile.rowSpan + " satır).");
                } else if (effectiveWhich == 5) {
                    int idx = config.tiles.indexOf(tile);
                    Tile spacer = new Tile(TileType.SPACER, "Boşluk", 0, 0, 1, 1);
                    insertTileAt(config, spacer, tile);
                    if (listener != null) listener.onSaveAndRefresh("Boşluk eklendi. Kumandayla üzerine gelerek boyutlandırabilirsiniz.");
                } else if (effectiveWhich == 6) {
                    if (hiddenApps.isEmpty()) {
                        UiUtils.showToast(activity, "Gizlenmiş uygulama yok. Tüm uygulamalarınız ekranda.");
                    } else {
                        openHiddenAppsDialog(activity, config, iconCache, tile, listener);
                    }
                } else if (effectiveWhich == 7) {
                    config.tiles.remove(tile);
                    if (listener != null) listener.onSaveAndRefresh(tile.label + " gizlendi. Ayarlar veya kutu menüsünden geri getirebilirsiniz.");
                } else if (effectiveWhich == 8) {
                    if (listener != null) listener.onOpenLauncherSettings();
                }
            }
        });
        builder.show();
    }

    private static void insertTileAt(WorkspaceConfig config, Tile newTile, Tile anchorTile) {
        if (anchorTile != null) {
            int idx = config.tiles.indexOf(anchorTile);
            if (idx >= 0 && idx < config.tiles.size()) {
                config.tiles.add(idx + 1, newTile);
            } else {
                config.tiles.add(newTile);
            }
        } else {
            config.tiles.add(newTile);
        }
    }
}
