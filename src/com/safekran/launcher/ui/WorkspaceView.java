package com.safekran.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import com.safekran.launcher.model.SafeArea;
import com.safekran.launcher.model.Tile;
import com.safekran.launcher.model.TileType;
import com.safekran.launcher.model.WorkspaceConfig;

import java.util.HashMap;
import java.util.Map;

public class WorkspaceView extends ViewGroup {

    private WorkspaceConfig config;
    private boolean isEditMode = false;
    private final Map<String, Drawable> iconCache = new HashMap<String, Drawable>();

    // Düzenleme modu çizim fırçaları
    private Paint safeAreaPaint;
    private Paint guidelinePaint;
    private Paint hudBgPaint;
    private Paint hudTextPaint;

    public WorkspaceView(Context context) {
        super(context);
        initPaints();
        setBackgroundColor(Color.BLACK);
        setClipChildren(false);
        setClipToPadding(false);
    }

    private void initPaints() {
        safeAreaPaint = new Paint();
        safeAreaPaint.setColor(Color.parseColor("#4DFF5722"));
        safeAreaPaint.setStyle(Paint.Style.STROKE);
        safeAreaPaint.setStrokeWidth(3f);
        safeAreaPaint.setPathEffect(new DashPathEffect(new float[]{15f, 10f}, 0));

        guidelinePaint = new Paint();
        guidelinePaint.setColor(Color.parseColor("#1AFFFFFF"));
        guidelinePaint.setStyle(Paint.Style.STROKE);
        guidelinePaint.setStrokeWidth(1f);

        hudBgPaint = new Paint();
        hudBgPaint.setColor(Color.parseColor("#E60D0D0D"));
        hudBgPaint.setStyle(Paint.Style.FILL);

        hudTextPaint = new Paint();
        hudTextPaint.setColor(Color.WHITE);
        hudTextPaint.setTextSize(26f);
        hudTextPaint.setAntiAlias(true);
    }

    public void setConfig(WorkspaceConfig config, Map<String, Drawable> icons, boolean editMode) {
        this.config = config;
        this.isEditMode = editMode;
        if (icons != null) {
            this.iconCache.putAll(icons);
        }
        rebuildViews();
    }

    public void setEditMode(boolean editMode) {
        if (this.isEditMode != editMode) {
            this.isEditMode = editMode;
            rebuildViews();
        }
    }

    public boolean isEditMode() {
        return isEditMode;
    }

    public WorkspaceConfig getConfig() {
        return config;
    }

    public void rebuildViews() {
        removeAllViews();
        if (config == null || config.tiles == null) return;

        for (Tile t : config.tiles) {
            Drawable icon = iconCache.get(t.packageName);
            TileView tv = new TileView(getContext(), t, isEditMode, icon);
            addView(tv);
        }
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (config != null) {
            SafeArea sa = config.safeArea;
            int usableWidth = width - (int) (width * sa.left) - (int) (width * sa.right);
            int usableHeight = height - (int) (height * sa.top) - (int) (height * sa.bottom);

            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child instanceof TileView) {
                    Tile t = ((TileView) child).getTile();

                    // Seçenek B: Serbest Yüzde (%10x%10 esnek kutu hesabı)
                    int childWidth = Math.max(10, (int) (usableWidth * t.widthPercent));
                    int childHeight = Math.max(10, (int) (usableHeight * t.heightPercent));

                    child.measure(
                            MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
                    );
                }
            }
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (config == null) return;

        int screenWidth = r - l;
        int screenHeight = b - t;

        SafeArea sa = config.safeArea;
        int leftOffset = (int) (screenWidth * sa.left);
        int topOffset = (int) (screenHeight * sa.top);
        int usableWidth = screenWidth - leftOffset - (int) (screenWidth * sa.right);
        int usableHeight = screenHeight - topOffset - (int) (screenHeight * sa.bottom);

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child instanceof TileView) {
                Tile tile = ((TileView) child).getTile();

                // Seçenek B: Serbest Yüzde pozisyonlaması
                int childLeft = leftOffset + (int) (usableWidth * tile.xPercent);
                int childTop = topOffset + (int) (usableHeight * tile.yPercent);
                int childRight = childLeft + child.getMeasuredWidth();
                int childBottom = childTop + child.getMeasuredHeight();

                child.layout(childLeft, childTop, childRight, childBottom);
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // 1. Zemin: Saf Siyah (#000000)
        canvas.drawColor(Color.BLACK);

        super.dispatchDraw(canvas);

        // 2. Düzenleme Modu Göstergeleri (Cetvel & Bilgi Kartı)
        if (isEditMode && config != null) {
            int w = getWidth();
            int h = getHeight();
            SafeArea sa = config.safeArea;

            int left = (int) (w * sa.left);
            int top = (int) (h * sa.top);
            int right = w - (int) (w * sa.right);
            int bottom = h - (int) (h * sa.bottom);

            // Güvenli alan sınır çizgisi
            canvas.drawRect(left, top, right, bottom, safeAreaPaint);

            // Kılavuz ızgara çizgileri (Yüzde 10'luk adımlarla)
            int usableW = right - left;
            int usableH = bottom - top;
            for (int i = 1; i <= 9; i++) {
                float gx = left + (usableW * (i * 0.10f));
                canvas.drawLine(gx, top, gx, bottom, guidelinePaint);
            }
            for (int i = 1; i <= 9; i++) {
                float gy = top + (usableH * (i * 0.10f));
                canvas.drawLine(left, gy, right, gy, guidelinePaint);
            }

            // TV Ergonomisi için Sağ Üstte Büyük Okunaklı Bilgi Kartı (HUD)
            String hudText1 = "DÜZENLEME MODU | Güvenli Alan: %" + (int) ((1f - sa.left - sa.right) * 100) + " x %" + (int) ((1f - sa.top - sa.bottom) * 100);
            String hudText2 = "Kutu Sayısı: " + config.tiles.size() + " | [OK]: Seç/Bırak | [Geri]: Çık";

            float cardW = 750f;
            float cardH = 90f;
            float cardRight = right - 10f;
            float cardLeft = cardRight - cardW;
            float cardTop = top + 10f;
            float cardBottom = cardTop + cardH;

            canvas.drawRect(cardLeft, cardTop, cardRight, cardBottom, hudBgPaint);
            canvas.drawText(hudText1, cardLeft + 20f, cardTop + 35f, hudTextPaint);
            canvas.drawText(hudText2, cardLeft + 20f, cardTop + 70f, hudTextPaint);
        }
    }
}
