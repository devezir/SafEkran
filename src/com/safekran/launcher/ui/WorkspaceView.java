package com.safekran.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.OverScroller;

import com.safekran.launcher.model.SafeArea;
import com.safekran.launcher.model.Tile;
import com.safekran.launcher.model.WorkspaceConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Saf Ekran Kesintisiz Akış Motoru (Continuous Auto-Flow Engine).
 * Sütun sayısı ve hücre konumları dinamik olarak hesaplanır.
 * Kutular asla üst üste binemez (Koleksiyon bazlı otomatik akış).
 * Ayar değiştiğinde veya ekran döndüğünde düzen kendini otomatik onarır.
 */
public class WorkspaceView extends ViewGroup {

    private WorkspaceConfig config;
    private boolean isEditMode = false;
    private final Map<String, Drawable> iconCache = new HashMap<String, Drawable>();

    // Dokunmatik ve Fiziksel Kaydırma (Touch & Fling)
    private final OverScroller scroller;
    private VelocityTracker velocityTracker;
    private final int touchSlop;
    private final int minFlingVelocity;
    private final int maxFlingVelocity;

    private float lastTouchY = 0f;
    private float initialDownY = 0f;
    private float initialDownX = 0f;
    private boolean isBeingDragged = false;
    private int maxScrollY = 0;

    // Matematiksel Olarak Türetilen Hücre Ölçüleri
    private int calculatedCols = 8;
    private int cellWidth = 0;
    private int cellHeight = 0;
    private int gutterXPx = 0;
    private int gutterYPx = 0;
    private int startLeft = 0;
    private int topOffset = 0;
    private int maxRow = 0;

    // Çizim fırçaları
    private Paint safeAreaPaint;
    private Paint cellGridPaint;
    private Paint hudBgPaint;
    private Paint hudTextPaint;
    private Paint scrollbarPaint;

    public WorkspaceView(Context context) {
        super(context);
        scroller = new OverScroller(context);
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minFlingVelocity = vc.getScaledMinimumFlingVelocity();
        maxFlingVelocity = vc.getScaledMaximumFlingVelocity();

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

        cellGridPaint = new Paint();
        cellGridPaint.setColor(Color.parseColor("#15FFFFFF"));
        cellGridPaint.setStyle(Paint.Style.STROKE);
        cellGridPaint.setStrokeWidth(1f);

        hudBgPaint = new Paint();
        hudBgPaint.setColor(Color.parseColor("#E60D0D0D"));
        hudBgPaint.setStyle(Paint.Style.FILL);

        hudTextPaint = new Paint();
        hudTextPaint.setColor(Color.WHITE);
        hudTextPaint.setTextSize(24f);
        hudTextPaint.setAntiAlias(true);

        scrollbarPaint = new Paint();
        scrollbarPaint.setColor(Color.parseColor("#66FFFFFF"));
        scrollbarPaint.setStyle(Paint.Style.FILL);
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

    public int getCalculatedCols() {
        return calculatedCols;
    }

    public void rebuildViews() {
        removeAllViews();
        if (config == null || config.tiles == null) return;

        for (Tile t : config.tiles) {
            Drawable icon = iconCache.get(t.packageName);
            TileView tv = new TileView(getContext(), t, config, isEditMode, icon);
            addView(tv);
        }
        requestLayout();
        invalidate();
    }

    private int getThemeBgColor() {
        if (config == null) return Color.BLACK;
        if ("LIGHT".equals(config.themeMode)) {
            return Color.parseColor("#EFEFEF");
        } else if ("DARK_GRAY".equals(config.themeMode)) {
            return Color.parseColor("#141414");
        }
        return Color.BLACK;
    }

    /**
     * KÖK NEDEN ÇÖZÜMÜ: Otomatik Akış ve Paketleme Algoritması (Auto-Flow Packing).
     * Kutuları mevcut sütun sayısına göre sırayla ve boşluksuz paketler.
     * Üst üste binme matematiksel olarak imkansız hale gelir.
     */
    private void packTilesAutomatically() {
        int curCol = 0;
        int curRow = 0;
        int cols = Math.max(1, calculatedCols);

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child instanceof TileView) {
                Tile t = ((TileView) child).getTile();
                int span = Math.min(cols, t.colSpan);

                // Eğer kutu mevcut satırın kalanına sığmıyorsa alt satıra geç
                if (curCol + span > cols) {
                    curCol = 0;
                    curRow++;
                }

                t.col = curCol;
                t.row = curRow;

                curCol += span;
                if (curCol >= cols) {
                    curCol = 0;
                    curRow++;
                }
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (config != null) {
            SafeArea sa = config.safeArea;
            int leftOff = (int) (width * sa.left);
            int rightOff = (int) (width * sa.right);
            topOffset = (int) (height * sa.top);
            int bottomOff = (int) (height * sa.bottom);

            int usableWidth = Math.max(100, width - leftOff - rightOff);
            int usableHeight = Math.max(100, height - topOffset - bottomOff);

            // Referans Boyut: Ekranın en uzun kenarı
            int refDim = Math.max(width, height);
            int targetTileW = Math.max(60, (int) (refDim * config.tileScale));

            gutterXPx = Math.max(0, (int) (usableWidth * config.gutterPercent));
            gutterYPx = Math.max(0, (int) (usableHeight * config.gutterPercent));

            // TÜRETİLEN SÜTUN SAYISI: (usableWidth + gutter) / (targetTileW + gutter)
            calculatedCols = Math.max(1, (usableWidth + gutterXPx) / (targetTileW + gutterXPx));

            // Hücre Genişliği (Kalan artık pikselleri tam yayan formül)
            int totalGuttersW = (calculatedCols - 1) * gutterXPx;
            cellWidth = Math.max(10, (usableWidth - totalGuttersW) / calculatedCols);

            // Hücre Yüksekliği (%75 En-Boy Oranı = TV Kartı)
            cellHeight = Math.max(10, (int) (cellWidth * config.tileHeightRatio));

            // Izgarayı ekranda tam ortalama
            int totalContentW = calculatedCols * cellWidth + totalGuttersW;
            int extraMarginX = Math.max(0, (usableWidth - totalContentW) / 2);
            startLeft = leftOff + extraMarginX;

            // Her ölçümde kutuları güncel sütun sayısına göre boşluksuz ve çakışmasız paketle
            packTilesAutomatically();

            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child instanceof TileView) {
                    Tile t = ((TileView) child).getTile();

                    int spanC = Math.min(calculatedCols, t.colSpan);
                    int childW = spanC * cellWidth + (spanC - 1) * gutterXPx;
                    int childH = t.rowSpan * cellHeight + (t.rowSpan - 1) * gutterYPx;

                    child.measure(
                            MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(childH, MeasureSpec.EXACTLY)
                    );
                }
            }
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (config == null) return;

        int screenHeight = b - t;
        int maxBottom = screenHeight;
        maxRow = 0;

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child instanceof TileView) {
                Tile tile = ((TileView) child).getTile();

                int childLeft = startLeft + tile.col * (cellWidth + gutterXPx);
                int childTop = topOffset + tile.row * (cellHeight + gutterYPx);
                int childRight = childLeft + child.getMeasuredWidth();
                int childBottom = childTop + child.getMeasuredHeight();

                child.layout(childLeft, childTop, childRight, childBottom);

                if (childBottom > maxBottom) {
                    maxBottom = childBottom;
                }
                if (tile.row + tile.rowSpan > maxRow) {
                    maxRow = tile.row + tile.rowSpan;
                }
            }
        }

        int bottomPadding = (int) (screenHeight * config.safeArea.bottom);
        maxScrollY = Math.max(0, (maxBottom + bottomPadding) - screenHeight);

        if (getScrollY() > maxScrollY) {
            scrollTo(0, maxScrollY);
        }
    }

    // ==========================================
    // DOKUNMATİK KAYDIRMA VE İVME MOTORU (TOUCH & FLING)
    // ==========================================

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            isBeingDragged = false;
            recycleVelocityTracker();
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                initialDownX = ev.getX();
                initialDownY = ev.getY();
                lastTouchY = ev.getY();
                isBeingDragged = !scroller.isFinished();
                initOrResetVelocityTracker();
                velocityTracker.addMovement(ev);
                break;

            case MotionEvent.ACTION_MOVE:
                float y = ev.getY();
                float dy = Math.abs(y - initialDownY);
                float dx = Math.abs(ev.getX() - initialDownX);

                if (dy > touchSlop && dy > dx) {
                    isBeingDragged = true;
                    lastTouchY = y;
                    initVelocityTrackerIfNotExists();
                    velocityTracker.addMovement(ev);

                    MotionEvent cancelEvent = MotionEvent.obtain(ev);
                    cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancelEvent);
                    cancelEvent.recycle();
                    return true;
                }
                break;
        }

        return isBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        initVelocityTrackerIfNotExists();
        velocityTracker.addMovement(ev);

        int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (!scroller.isFinished()) {
                    scroller.abortAnimation();
                }
                lastTouchY = ev.getY();
                initialDownY = ev.getY();
                return true;

            case MotionEvent.ACTION_MOVE:
                float y = ev.getY();
                float deltaY = lastTouchY - y;

                if (!isBeingDragged && Math.abs(y - initialDownY) > touchSlop) {
                    isBeingDragged = true;
                }

                if (isBeingDragged) {
                    int currentScrollY = getScrollY();
                    int newScrollY = (int) (currentScrollY + deltaY);

                    if (newScrollY < 0) {
                        newScrollY = 0;
                    } else if (newScrollY > maxScrollY) {
                        newScrollY = maxScrollY;
                    }

                    scrollTo(0, newScrollY);
                    lastTouchY = y;
                }
                break;

            case MotionEvent.ACTION_UP:
                if (isBeingDragged) {
                    velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                    float yVelocity = velocityTracker.getYVelocity();

                    if (Math.abs(yVelocity) > minFlingVelocity) {
                        scroller.fling(0, getScrollY(), 0, (int) -yVelocity, 0, 0, 0, maxScrollY);
                        postInvalidateOnAnimation();
                    }
                    isBeingDragged = false;
                }
                recycleVelocityTracker();
                break;

            case MotionEvent.ACTION_CANCEL:
                isBeingDragged = false;
                recycleVelocityTracker();
                break;
        }

        return true;
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            int currY = scroller.getCurrY();
            scrollTo(0, currY);
            postInvalidateOnAnimation();
        }
    }

    // ==========================================
    // TV KUMANDASI ODAK TAKİBİ VE OTO-KAYDIRMA
    // ==========================================

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);

        if (focused != null && config != null) {
            int childTop = child.getTop();
            int childBottom = child.getBottom();
            int currentScrollY = getScrollY();
            int viewH = getHeight();

            int topMargin = (int) (viewH * config.safeArea.top) + 20;
            int bottomMargin = (int) (viewH * config.safeArea.bottom) + 20;

            if (childBottom > currentScrollY + viewH - bottomMargin) {
                int targetY = Math.min(maxScrollY, childBottom - viewH + bottomMargin);
                smoothScrollTo(targetY);
            } else if (childTop < currentScrollY + topMargin) {
                int targetY = Math.max(0, childTop - topMargin);
                smoothScrollTo(targetY);
            }
        }
    }

    public void smoothScrollTo(int targetY) {
        int dy = targetY - getScrollY();
        scroller.startScroll(0, getScrollY(), 0, dy, 250);
        postInvalidateOnAnimation();
    }

    private void initOrResetVelocityTracker() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        } else {
            velocityTracker.clear();
        }
    }

    private void initVelocityTrackerIfNotExists() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.drawColor(getThemeBgColor());

        super.dispatchDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        int scrollY = getScrollY();

        // Minimalist Scrollbar
        if (maxScrollY > 0) {
            float totalH = h + maxScrollY;
            float barH = Math.max(40f, (h / totalH) * h);
            float barY = scrollY + ((float) scrollY / maxScrollY) * (h - barH);
            float barW = 6f;
            float barRight = w - 8f;
            canvas.drawRect(barRight - barW, barY, barRight, barY + barH, scrollbarPaint);
        }

        // Düzenleme Modu: Otomatik Türetilen Hücre Izgarası & HUD
        if (isEditMode && config != null) {
            SafeArea sa = config.safeArea;
            int left = (int) (w * sa.left);
            int top = (int) (h * sa.top) + scrollY;
            int right = w - (int) (w * sa.right);
            int bottom = (h - (int) (h * sa.bottom)) + scrollY;

            canvas.drawRect(left, top, right, bottom, safeAreaPaint);

            int rowsToDraw = Math.max(10, maxRow + 2);

            for (int rIndex = 0; rIndex < rowsToDraw; rIndex++) {
                int cellTop = (top - scrollY) + rIndex * (cellHeight + gutterYPx);
                for (int cIndex = 0; cIndex < calculatedCols; cIndex++) {
                    int cLeft = startLeft + cIndex * (cellWidth + gutterXPx);
                    canvas.drawRect(cLeft, cellTop, cLeft + cellWidth, cellTop + cellHeight, cellGridPaint);
                }
            }

            String hudText1 = "DÜZENLEME MODU | Otomatik Akış: " + calculatedCols + " Sütun";
            String hudText2 = "Hücre: " + cellWidth + "x" + cellHeight + " px | [Yön Tuşu]: Sıra Değiştir | [OK]: Bırak";

            float cardW = 760f;
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
