package com.safekran.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.OverScroller;

import com.safekran.launcher.model.SafeArea;
import com.safekran.launcher.model.Tile;
import com.safekran.launcher.model.TileType;
import com.safekran.launcher.model.WorkspaceConfig;
import com.safekran.launcher.util.UiUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Saf Ekran Kilitli Pencere Izgara Motoru (Locked Viewport Grid Engine).
 * Güvenli Alan (Overscan Inset) fiziksel ekran camının 4 kenarına mıh gibi çakılıdır.
 * İçerik kaydırılsa dahi alt güvenli alan listenin kuyruğuna değil, ekranın altına kilitlidir.
 * Güvenli alanın dışındaki kör bölgeler saf siyah kalır ve içerik oraya asla taşamaz (Hardware Clip).
 */
public class WorkspaceView extends ViewGroup {

    private WorkspaceConfig config;
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

    // Matematiksel Olarak Türetilen Hücre ve Pencere Ölçüleri
    private int calculatedCols = 8;
    private int cellWidth = 0;
    private int cellHeight = 0;
    private int gutterXPx = 0;
    private int gutterYPx = 0;

    // Ekrana Çakılı Güvenli Alan Sınırları (Piksel cinsinden)
    private int safeLeft = 0;
    private int safeRight = 0;
    private int safeTop = 0;
    private int safeBottom = 0;
    private int startLeft = 0;
    private int maxRow = 0;

    // Çizim fırçaları
    private Paint scrollbarPaint;

    // TV Seçim İmleci (TV Focus Cursor)
    private Paint tvCursorPaint;
    private Paint tvCursorGlowPaint;
    private final RectF tvCursorRect = new RectF();
    private TileView tvSelectedTile = null;

    public WorkspaceView(Context context) {
        super(context);
        scroller = new OverScroller(context);
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minFlingVelocity = vc.getScaledMinimumFlingVelocity();
        maxFlingVelocity = vc.getScaledMaximumFlingVelocity();

        initPaints();
        setBackgroundColor(Color.BLACK);
        setClipChildren(true);
        setClipToPadding(true);
    }

    private void initPaints() {
        scrollbarPaint = new Paint();
        scrollbarPaint.setColor(Color.parseColor("#66FFFFFF"));
        scrollbarPaint.setStyle(Paint.Style.FILL);

        // TV Seçim İmleci (TV Focus Cursor)
        tvCursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tvCursorPaint.setStyle(Paint.Style.STROKE);
        tvCursorPaint.setStrokeWidth(dpToPx(3.5f));
        tvCursorPaint.setColor(Color.WHITE);

        tvCursorGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tvCursorGlowPaint.setStyle(Paint.Style.STROKE);
        tvCursorGlowPaint.setStrokeWidth(dpToPx(7f));
        tvCursorGlowPaint.setColor(Color.parseColor("#4DFFFFFF"));
    }

    private float dpToPx(float dp) {
        return UiUtils.dpToPxFloat(getContext(), dp);
    }

    public void setConfig(WorkspaceConfig config, Map<String, Drawable> icons) {
        this.config = config;
        if (icons != null) {
            this.iconCache.putAll(icons);
        }
        rebuildViews();
    }

    @Deprecated
    public void setConfig(WorkspaceConfig config, Map<String, Drawable> icons, boolean editMode) {
        setConfig(config, icons);
    }

    public WorkspaceConfig getConfig() {
        return config;
    }

    public int getCalculatedCols() {
        return calculatedCols;
    }

    public interface TileVisitor {
        void visit(TileView tileView);
    }

    /**
     * Çalışma alanındaki tüm TileView çocuklarını gezen merkezi ziyaretçi döngüsü.
     */
    public void forEachTile(TileVisitor visitor) {
        if (visitor == null) return;
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child instanceof TileView) {
                visitor.visit((TileView) child);
            }
        }
    }

    public void updateAllTileFontSizes() {
        forEachTile(new TileVisitor() {
            @Override
            public void visit(TileView tv) {
                tv.refreshDynamicSizes();
            }
        });
    }

    public void rebuildViews() {
        removeAllViews();
        tvSelectedTile = null;
        if (config == null || config.tiles == null) return;

        for (Tile t : config.tiles) {
            Drawable icon = iconCache.get(t.packageName);
            TileView tv = new TileView(getContext(), t, config, icon);
            addView(tv);
        }
        requestLayout();
        invalidate();
        if (config.virtualRemoteEnabled) {
            post(new Runnable() {
                @Override
                public void run() {
                    selectFirstVisibleTile();
                }
            });
        }
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
     * 2 Boyutlu Doluluk Izgarası (2D Occupancy Grid Auto-Flow Engine):
     * Kutuların hem yatay (colSpan) hem dikey (rowSpan) boyutlarını 2D haritada mühürler.
     * Dikeyde 2x, 3x büyüyen kutuların altındaki hücreler dolu işaretlenir;
     * alt satırdaki kutular dolu hücreleri otomatik atlar ve ASLA üst üste binme oluşamaz!
     */
    private void packTilesAutomatically() {
        int cols = Math.max(1, calculatedCols);
        java.util.List<boolean[]> grid = new java.util.ArrayList<boolean[]>();

        int curRow = 0;
        int curCol = 0;

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child instanceof TileView) {
                Tile t = ((TileView) child).getTile();
                int spanC = Math.min(cols, Math.max(1, t.colSpan));
                int spanR = Math.max(1, t.rowSpan);

                // Bu kutu için sığabileceği ilk boş hücreyi bul
                while (true) {
                    if (curCol + spanC > cols) {
                        // Sütun sınırını aşıyor, alt satırın başına geç
                        curCol = 0;
                        curRow++;
                        continue;
                    }

                    // Alanın tüm hücreleri boş mu kontrol et
                    boolean fits = true;
                    for (int r = curRow; r < curRow + spanR; r++) {
                        for (int c = curCol; c < curCol + spanC; c++) {
                            if (isCellOccupied(grid, r, c)) {
                                fits = false;
                                break;
                            }
                        }
                        if (!fits) break;
                    }

                    if (fits) {
                        // Boş yer bulundu! Koordinatları ata
                        t.col = curCol;
                        t.row = curRow;

                        // Grid üzerinde tüm hücreleri DOLU olarak işaretle
                        markOccupied(grid, curRow, curCol, spanC, spanR, cols);

                        // Bir sonraki arama için curCol'u ilerlet
                        curCol += spanC;
                        if (curCol >= cols) {
                            curCol = 0;
                            curRow++;
                        }
                        break;
                    } else {
                        // Bu hücre dolu (örneğin yukarıdan sarkan başka bir kutu var)
                        // Sağa doğru 1 hücre kayarak boş yer ara
                        curCol++;
                        if (curCol >= cols) {
                            curCol = 0;
                            curRow++;
                        }
                    }
                }
            }
        }
    }

    private boolean isCellOccupied(java.util.List<boolean[]> grid, int r, int c) {
        if (r >= grid.size()) return false;
        return grid.get(r)[c];
    }

    private void markOccupied(java.util.List<boolean[]> grid, int startR, int startC, int spanC, int spanR, int cols) {
        while (grid.size() < startR + spanR) {
            grid.add(new boolean[cols]);
        }
        for (int r = startR; r < startR + spanR; r++) {
            boolean[] rowArray = grid.get(r);
            for (int c = startC; c < startC + spanC; c++) {
                rowArray[c] = true;
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (config != null) {
            SafeArea sa = config.safeArea;
            safeLeft = (int) (width * sa.left);
            safeRight = (int) (width * sa.right);
            safeTop = (int) (height * sa.top);
            safeBottom = (int) (height * sa.bottom);

            // Fiziksel Ekran Camına Kilitli Kullanılabilir Alan
            int usableWidth = Math.max(100, width - safeLeft - safeRight);
            int usableHeight = Math.max(100, height - safeTop - safeBottom);

            // TV (Yatay) ve Telefon (Dikey) Ekran Oranı Dengesi:
            // Telefonda dikey modda aşırı yüksek ekran boyu nedeniyle kutuların yapay büyümesini önlemek için
            // referans boyut genişliğe normalize edilir (16:9 standardı).
            int refDim = (width >= height) ? width : (int) (width * 1.777f);
            int targetTileW = Math.max(60, (int) (refDim * config.tileScale));

            gutterXPx = Math.max(0, (int) (usableWidth * config.gutterPercent));
            gutterYPx = Math.max(0, (int) (usableHeight * config.gutterPercent));

            // Türetilen Sütun Sayısı (Dikey telefon modunda asgari 4 sütun güvencesi)
            int minCols = (height > width) ? 4 : 2;
            calculatedCols = Math.max(minCols, (usableWidth + gutterXPx) / (targetTileW + gutterXPx));

            // Hücre Genişliği
            int totalGuttersW = (calculatedCols - 1) * gutterXPx;
            cellWidth = Math.max(10, (usableWidth - totalGuttersW) / calculatedCols);

            // Hücre Yüksekliği
            cellHeight = Math.max(10, (int) (cellWidth * config.tileHeightRatio));

            // Kalan artığı yatayda tam ortalama
            int totalContentW = calculatedCols * cellWidth + totalGuttersW;
            int extraMarginX = Math.max(0, (usableWidth - totalContentW) / 2);
            startLeft = safeLeft + extraMarginX;

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
        int maxBottom = 0;
        maxRow = 0;

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child instanceof TileView) {
                Tile tile = ((TileView) child).getTile();

                int childLeft = startLeft + tile.col * (cellWidth + gutterXPx);
                int childTop = safeTop + tile.row * (cellHeight + gutterYPx);
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

        // KİLİTLİ PENCERE FORMÜLÜ:
        // Alt Güvenli Alan ekranın fiziksel camının altına kilitlidir.
        // Kaydırma (Scroll) sınırının bittiği yer = En alttaki kutunun tabanı - (Ekran Yüksekliği - Alt Güvenli Payı)
        int visibleWindowBottom = screenHeight - safeBottom;
        maxScrollY = Math.max(0, maxBottom - visibleWindowBottom);

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

        if (child instanceof TileView) {
            TileView target = (TileView) child;
            if (tvSelectedTile != null && tvSelectedTile != target) {
                tvSelectedTile.setTvFocused(false);
            }
            tvSelectedTile = target;
            tvSelectedTile.setSelected(true);
            postInvalidateOnAnimation();
        }

        if (focused != null && config != null) {
            scrollChildIntoView(child.getTop(), child.getBottom(), 0);
        }
    }

    public void scrollChildIntoView(int childTop, int childBottom, int buffer) {
        if (config == null) return;
        int currentScrollY = getScrollY();
        int viewH = getHeight();

        int visibleWindowBottom = viewH - safeBottom - buffer;
        int visibleWindowTop = safeTop + buffer;

        if (childBottom - currentScrollY > visibleWindowBottom) {
            int targetY = Math.min(maxScrollY, childBottom - visibleWindowBottom + buffer);
            smoothScrollTo(targetY);
        } else if (childTop - currentScrollY < visibleWindowTop) {
            int targetY = Math.max(0, childTop - visibleWindowTop);
            smoothScrollTo(targetY);
        }
    }

    public void smoothScrollTo(int targetY) {
        if (!scroller.isFinished()) {
            scroller.abortAnimation();
        }
        int dy = targetY - getScrollY();
        scroller.startScroll(0, getScrollY(), 0, dy, 150);
        postInvalidateOnAnimation();
    }

    // ==========================================
    // TV SEÇİM İMLECİ & 2D UZAYSAL GEZİNTİ MOTORU
    // ==========================================

    public TileView findTileView(Tile tile) {
        if (tile == null) return null;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof TileView) {
                TileView tv = (TileView) child;
                if (tv.getTile() == tile) {
                    return tv;
                }
            }
        }
        return null;
    }

    public void setTvSelectedTile(TileView target) {
        if (tvSelectedTile != null && tvSelectedTile != target) {
            tvSelectedTile.setTvFocused(false);
        }
        tvSelectedTile = target;
        if (tvSelectedTile != null) {
            tvSelectedTile.setTvFocused(true);
            ensureTileVisible(tvSelectedTile);
        }
        postInvalidateOnAnimation();
    }

    public TileView getTvSelectedTile() {
        return tvSelectedTile;
    }

    public void clearTvSelection() {
        if (tvSelectedTile != null) {
            tvSelectedTile.setTvFocused(false);
            tvSelectedTile = null;
            postInvalidateOnAnimation();
        }
    }

    public void ensureTileVisible(TileView target) {
        if (target == null) return;
        scrollChildIntoView(target.getTop(), target.getBottom(), (int) dpToPx(24));
    }

    public TileView selectFirstVisibleTile() {
        if (getChildCount() == 0) return null;

        final int currentScrollY = getScrollY();
        final TileView[] bestCandidate = new TileView[1];
        final TileView[] targetFound = new TileView[1];

        forEachTile(new TileVisitor() {
            @Override
            public void visit(TileView tv) {
                if (targetFound[0] != null || tv.getVisibility() != View.VISIBLE) return;
                Tile t = tv.getTile();
                if (t != null && t.type != TileType.SPACER) {
                    if (bestCandidate[0] == null) {
                        bestCandidate[0] = tv;
                    }
                    if (tv.getBottom() > currentScrollY + safeTop) {
                        targetFound[0] = tv;
                    }
                }
            }
        });

        TileView chosen = (targetFound[0] != null) ? targetFound[0] : bestCandidate[0];
        if (chosen != null) {
            setTvSelectedTile(chosen);
        }
        return chosen;
    }

    public void executeTvSelected() {
        TileView target = resolveTvTargetTile();
        if (target != null) {
            target.performClick();
        }
    }

    public void executeTvSelectedLongClick() {
        TileView target = resolveTvTargetTile();
        if (target != null) {
            target.performLongClick();
        }
    }

    private TileView resolveTvTargetTile() {
        View focused = findFocus();
        if (focused instanceof TileView) {
            tvSelectedTile = (TileView) focused;
            return tvSelectedTile;
        }
        if (tvSelectedTile != null) {
            return tvSelectedTile;
        }
        return selectFirstVisibleTile();
    }

    public void navigateTv(int keyCode) {
        int count = getChildCount();
        if (count == 0) return;

        if (tvSelectedTile == null || tvSelectedTile.getVisibility() != View.VISIBLE || tvSelectedTile.getParent() != this) {
            selectFirstVisibleTile();
            return;
        }

        Tile curTile = tvSelectedTile.getTile();
        if (curTile == null) {
            selectFirstVisibleTile();
            return;
        }

        final java.util.List<TileView> validTiles = new java.util.ArrayList<TileView>();
        forEachTile(new TileVisitor() {
            @Override
            public void visit(TileView tv) {
                if (tv.getVisibility() == View.VISIBLE && tv.getTile() != null) {
                    validTiles.add(tv);
                }
            }
        });

        if (validTiles.isEmpty()) return;

        TileView targetTile = null;
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            targetTile = findHorizontalTarget(validTiles, curTile, true);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            targetTile = findHorizontalTarget(validTiles, curTile, false);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            targetTile = findVerticalTarget(validTiles, curTile, true);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            targetTile = findVerticalTarget(validTiles, curTile, false);
        }

        if (targetTile != null) {
            setTvSelectedTile(targetTile);
        }
    }

    private TileView findHorizontalTarget(java.util.List<TileView> validTiles, Tile curTile, boolean isRight) {
        TileView sameRowTarget = null;
        TileView wrapTarget = null;

        for (TileView tv : validTiles) {
            Tile t = tv.getTile();
            if (t.row == curTile.row) {
                if (isRight && t.col > curTile.col) {
                    if (sameRowTarget == null || t.col < sameRowTarget.getTile().col) {
                        sameRowTarget = tv;
                    }
                } else if (!isRight && t.col < curTile.col) {
                    if (sameRowTarget == null || t.col > sameRowTarget.getTile().col) {
                        sameRowTarget = tv;
                    }
                }
            } else if (isRight && t.row > curTile.row) {
                if (wrapTarget == null || t.row < wrapTarget.getTile().row ||
                        (t.row == wrapTarget.getTile().row && t.col < wrapTarget.getTile().col)) {
                    wrapTarget = tv;
                }
            } else if (!isRight && t.row < curTile.row) {
                if (wrapTarget == null || t.row > wrapTarget.getTile().row ||
                        (t.row == wrapTarget.getTile().row && t.col > wrapTarget.getTile().col)) {
                    wrapTarget = tv;
                }
            }
        }
        return (sameRowTarget != null) ? sameRowTarget : wrapTarget;
    }

    private TileView findVerticalTarget(java.util.List<TileView> validTiles, Tile curTile, boolean isDown) {
        int curRefRow = isDown ? (curTile.row + curTile.rowSpan - 1) : curTile.row;
        int minDeltaRow = Integer.MAX_VALUE;

        // 1. En yakın hedef satır mesafesini bul
        for (TileView tv : validTiles) {
            Tile t = tv.getTile();
            int tRefRow = isDown ? t.row : (t.row + t.rowSpan - 1);
            int dRow = isDown ? (tRefRow - curRefRow) : (curRefRow - tRefRow);
            if (dRow > 0 && dRow < minDeltaRow) {
                minDeltaRow = dRow;
            }
        }

        if (minDeltaRow == Integer.MAX_VALUE) return null;

        int targetRefRow = isDown ? (curRefRow + minDeltaRow) : (curRefRow - minDeltaRow);
        TileView bestTile = null;
        int minColDist = Integer.MAX_VALUE;

        // 2. O satırdaki sütun mesafesi en yakın kutuyu seç
        for (TileView tv : validTiles) {
            Tile t = tv.getTile();
            int tRefRow = isDown ? t.row : (t.row + t.rowSpan - 1);
            if (tRefRow == targetRefRow) {
                int dist = Math.abs(t.col - curTile.col);
                if (dist < minColDist) {
                    minColDist = dist;
                    bestTile = tv;
                }
            }
        }
        return bestTile;
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
        int w = getWidth();
        int h = getHeight();
        int scrollY = getScrollY();

        // 1. Zemin: Saf Siyah (#000000)
        canvas.drawColor(getThemeBgColor());

        // 2. KİLİTLİ GÜVENLİ ALAN PENCERESİNE KIRP (HARDWARE CLIP)
        // Kutucuklar sadece bu kilitli pencerenin içinde akar;
        // Alt, üst, sol ve sağ güvenli alan kör bölgelerine ASLA taşamaz!
        int saveCount = canvas.save();
        canvas.clipRect(safeLeft, safeTop + scrollY, w - safeRight, (h - safeBottom) + scrollY);

        super.dispatchDraw(canvas);

        // TV SEÇİM İMLECİ (TV FOCUS CURSOR) ÇİZİMİ
        if (tvSelectedTile != null && tvSelectedTile.getVisibility() == View.VISIBLE && tvSelectedTile.getParent() == this) {
            float cx = (tvSelectedTile.getLeft() + tvSelectedTile.getRight()) / 2f;
            float cy = (tvSelectedTile.getTop() + tvSelectedTile.getBottom()) / 2f;
            float scaleX = tvSelectedTile.getScaleX();
            float scaleY = tvSelectedTile.getScaleY();
            float halfW = (tvSelectedTile.getWidth() / 2f) * scaleX;
            float halfH = (tvSelectedTile.getHeight() / 2f) * scaleY;

            float r = dpToPx(6f);
            tvCursorRect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH);

            boolean isLight = config != null && "LIGHT".equals(config.themeMode);
            tvCursorPaint.setColor(isLight ? Color.parseColor("#007AFF") : Color.WHITE);
            tvCursorGlowPaint.setColor(isLight ? Color.parseColor("#33007AFF") : Color.parseColor("#66FFFFFF"));

            // 1. Neon Dış Hale (Yumuşak ve Parlak)
            canvas.drawRoundRect(tvCursorRect, r + dpToPx(2.5f), r + dpToPx(2.5f), tvCursorGlowPaint);
            // 2. Parlak Keskin TV Çerçevesi
            canvas.drawRoundRect(tvCursorRect, r, r, tvCursorPaint);
        }

        canvas.restoreToCount(saveCount);

        // 3. Minimalist Scrollbar (Kilitli Güvenli Alan Sınırları İçinde)
        int visibleWindowH = (h - safeBottom) - safeTop;
        if (maxScrollY > 0 && visibleWindowH > 0) {
            boolean isLight = config != null && "LIGHT".equals(config.themeMode);
            scrollbarPaint.setColor(isLight ? Color.parseColor("#66000000") : Color.parseColor("#66FFFFFF"));
            float totalContentH = visibleWindowH + maxScrollY;
            float barH = Math.max(30f, ((float) visibleWindowH / totalContentH) * visibleWindowH);
            float barY = safeTop + scrollY + ((float) scrollY / maxScrollY) * (visibleWindowH - barH);
            float barW = 5f;
            float barRight = (w - safeRight) - 2f;
            canvas.drawRect(barRight - barW, barY, barRight, barY + barH, scrollbarPaint);
        }
    }

    public void updateBatteryStatus(final int pct, final boolean isCharging) {
        forEachTile(new TileVisitor() {
            @Override
            public void visit(TileView tv) {
                if (tv.getTile() != null && tv.getTile().type == TileType.BATTERY) {
                    tv.updateBatteryData(pct, isCharging);
                }
            }
        });
    }

    public void refreshStatusTiles() {
        forEachTile(new TileVisitor() {
            @Override
            public void visit(TileView tv) {
                if (tv.getTile() != null && tv.getTile().isStatusTile()) {
                    tv.refreshStatusData();
                }
            }
        });
    }

    public void playTurnstileAnimation() {
        if (config == null || !config.animationEnabled) return;
        post(new Runnable() {
            @Override
            public void run() {
                if (getChildCount() == 0) return;
                forEachTile(new TileVisitor() {
                    @Override
                    public void visit(TileView tv) {
                        Tile t = tv.getTile();
                        if (t == null) return;
                        // Orijinal Windows Phone Cascade Turnstile:
                        // Sağ-çaprazdan belirgin süzülme ve yumuşak yerine oturma
                        tv.setAlpha(0f);
                        tv.setTranslationX(dpToPx(75f));
                        tv.setTranslationY(dpToPx(25f));
                        tv.setScaleX(0.88f);
                        tv.setScaleY(0.88f);

                        int delay = Math.min(380, (t.row * 40) + (t.col * 22));
                        tv.animate()
                                .alpha(1f)
                                .translationX(0f)
                                .translationY(0f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setStartDelay(delay)
                                .setDuration(420)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                                .start();
                    }
                });
            }
        });
    }
}
