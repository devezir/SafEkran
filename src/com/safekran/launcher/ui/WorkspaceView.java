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
    private Paint safeAreaPaint;
    private Paint cellGridPaint;
    private Paint hudBgPaint;
    private Paint hudTextPaint;
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
        return dp * getContext().getResources().getDisplayMetrics().density;
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
        tvSelectedTile = null;
        if (config == null || config.tiles == null) return;

        for (Tile t : config.tiles) {
            Drawable icon = iconCache.get(t.packageName);
            TileView tv = new TileView(getContext(), t, config, isEditMode, icon);
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

            int refDim = Math.max(width, height);
            int targetTileW = Math.max(60, (int) (refDim * config.tileScale));

            gutterXPx = Math.max(0, (int) (usableWidth * config.gutterPercent));
            gutterYPx = Math.max(0, (int) (usableHeight * config.gutterPercent));

            // Türetilen Sütun Sayısı
            calculatedCols = Math.max(1, (usableWidth + gutterXPx) / (targetTileW + gutterXPx));

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

            int visibleWindowBottom = viewH - safeBottom;

            if (childBottom - currentScrollY > visibleWindowBottom) {
                int targetY = Math.min(maxScrollY, childBottom - visibleWindowBottom);
                smoothScrollTo(targetY);
            } else if (childTop - currentScrollY < safeTop) {
                int targetY = Math.max(0, childTop - safeTop);
                smoothScrollTo(targetY);
            }
        }
    }

    public void smoothScrollTo(int targetY) {
        int dy = targetY - getScrollY();
        scroller.startScroll(0, getScrollY(), 0, dy, 250);
        postInvalidateOnAnimation();
    }

    // ==========================================
    // TV SEÇİM İMLECİ & 2D UZAYSAL GEZİNTİ MOTORU
    // ==========================================

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
        if (target == null || config == null) return;
        int childTop = target.getTop();
        int childBottom = target.getBottom();
        int currentScrollY = getScrollY();
        int viewH = getHeight();

        int visibleWindowBottom = viewH - safeBottom;

        if (childBottom - currentScrollY > visibleWindowBottom) {
            int targetY = Math.min(maxScrollY, childBottom - visibleWindowBottom);
            smoothScrollTo(targetY);
        } else if (childTop - currentScrollY < safeTop) {
            int targetY = Math.max(0, childTop - safeTop);
            smoothScrollTo(targetY);
        }
    }

    public TileView selectFirstVisibleTile() {
        int count = getChildCount();
        if (count == 0) return null;

        int currentScrollY = getScrollY();
        TileView bestCandidate = null;

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child instanceof TileView && child.getVisibility() == View.VISIBLE) {
                TileView tv = (TileView) child;
                Tile t = tv.getTile();
                if (t != null && t.type != TileType.SPACER) {
                    if (bestCandidate == null) {
                        bestCandidate = tv;
                    }
                    if (tv.getBottom() > currentScrollY + safeTop) {
                        setTvSelectedTile(tv);
                        return tv;
                    }
                }
            }
        }

        if (bestCandidate != null) {
            setTvSelectedTile(bestCandidate);
            return bestCandidate;
        }
        return null;
    }

    public void executeTvSelected() {
        if (tvSelectedTile != null) {
            tvSelectedTile.performClick();
        } else {
            selectFirstVisibleTile();
        }
    }

    public void executeTvSelectedLongClick() {
        if (tvSelectedTile != null) {
            tvSelectedTile.performLongClick();
        } else {
            selectFirstVisibleTile();
            if (tvSelectedTile != null) {
                tvSelectedTile.performLongClick();
            }
        }
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

        java.util.List<TileView> validTiles = new java.util.ArrayList<TileView>();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child instanceof TileView && child.getVisibility() == View.VISIBLE) {
                TileView tv = (TileView) child;
                Tile t = tv.getTile();
                if (t != null && (isEditMode || t.type != TileType.SPACER)) {
                    validTiles.add(tv);
                }
            }
        }

        if (validTiles.isEmpty()) return;

        TileView targetTile = null;

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            TileView nextInRow = null;
            TileView nextWrap = null;
            for (TileView tv : validTiles) {
                Tile t = tv.getTile();
                if (t.row == curTile.row && t.col > curTile.col) {
                    if (nextInRow == null || t.col < nextInRow.getTile().col) {
                        nextInRow = tv;
                    }
                } else if (t.row > curTile.row) {
                    if (nextWrap == null || t.row < nextWrap.getTile().row ||
                            (t.row == nextWrap.getTile().row && t.col < nextWrap.getTile().col)) {
                        nextWrap = tv;
                    }
                }
            }
            targetTile = (nextInRow != null) ? nextInRow : nextWrap;

        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            TileView prevInRow = null;
            TileView prevWrap = null;
            for (TileView tv : validTiles) {
                Tile t = tv.getTile();
                if (t.row == curTile.row && t.col < curTile.col) {
                    if (prevInRow == null || t.col > prevInRow.getTile().col) {
                        prevInRow = tv;
                    }
                } else if (t.row < curTile.row) {
                    if (prevWrap == null || t.row > prevWrap.getTile().row ||
                            (t.row == prevWrap.getTile().row && t.col > prevWrap.getTile().col)) {
                        prevWrap = tv;
                    }
                }
            }
            targetTile = (prevInRow != null) ? prevInRow : prevWrap;

        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            int minDeltaRow = Integer.MAX_VALUE;
            for (TileView tv : validTiles) {
                Tile t = tv.getTile();
                if (t.row > curTile.row) {
                    int dRow = t.row - curTile.row;
                    if (dRow < minDeltaRow) {
                        minDeltaRow = dRow;
                    }
                }
            }
            if (minDeltaRow != Integer.MAX_VALUE) {
                int targetRow = curTile.row + minDeltaRow;
                int minColDist = Integer.MAX_VALUE;
                for (TileView tv : validTiles) {
                    Tile t = tv.getTile();
                    if (t.row == targetRow) {
                        int dist = Math.abs(t.col - curTile.col);
                        if (dist < minColDist) {
                            minColDist = dist;
                            targetTile = tv;
                        }
                    }
                }
            }

        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            int minDeltaRow = Integer.MAX_VALUE;
            for (TileView tv : validTiles) {
                Tile t = tv.getTile();
                if (t.row < curTile.row) {
                    int dRow = curTile.row - t.row;
                    if (dRow < minDeltaRow) {
                        minDeltaRow = dRow;
                    }
                }
            }
            if (minDeltaRow != Integer.MAX_VALUE) {
                int targetRow = curTile.row - minDeltaRow;
                int minColDist = Integer.MAX_VALUE;
                for (TileView tv : validTiles) {
                    Tile t = tv.getTile();
                    if (t.row == targetRow) {
                        int dist = Math.abs(t.col - curTile.col);
                        if (dist < minColDist) {
                            minColDist = dist;
                            targetTile = tv;
                        }
                    }
                }
            }
        }

        if (targetTile != null) {
            setTvSelectedTile(targetTile);
        }
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

        // 4. DÜZENLEME MODU: CAMA ÇAKILI KILAVUZ ÇİZGİLERİ VE HUD
        if (isEditMode && config != null) {
            boolean isLight = "LIGHT".equals(config.themeMode);
            cellGridPaint.setColor(isLight ? Color.parseColor("#25000000") : Color.parseColor("#15FFFFFF"));

            // DİKKAT: Kırmızı çerçeve ekran camına çakılıdır (scrollY EKLENMEZ!)
            canvas.drawRect(safeLeft, safeTop + scrollY, w - safeRight, (h - safeBottom) + scrollY, safeAreaPaint);

            // Kılavuz Hücre Çizgileri
            int rowsToDraw = Math.max(10, maxRow + 2);
            for (int rIndex = 0; rIndex < rowsToDraw; rIndex++) {
                int cellTop = safeTop + rIndex * (cellHeight + gutterYPx);
                for (int cIndex = 0; cIndex < calculatedCols; cIndex++) {
                    int cLeft = startLeft + cIndex * (cellWidth + gutterXPx);
                    canvas.drawRect(cLeft, cellTop, cLeft + cellWidth, cellTop + cellHeight, cellGridPaint);
                }
            }

            // TV Ergonomisi HUD
            String hudText1 = "DÜZENLEME MODU | Kilitli Pencere: " + calculatedCols + " Sütun";
            String hudText2 = "Alt Güvenlik: %" + (int)(config.safeArea.bottom * 100) + " | Cam Tabanına Çakılı";

            float cardW = 760f;
            float cardH = 90f;
            float cardRight = (w - safeRight) - 10f;
            float cardLeft = cardRight - cardW;
            float cardTop = safeTop + scrollY + 10f;
            float cardBottom = cardTop + cardH;

            canvas.drawRect(cardLeft, cardTop, cardRight, cardBottom, hudBgPaint);
            canvas.drawText(hudText1, cardLeft + 20f, cardTop + 35f, hudTextPaint);
            canvas.drawText(hudText2, cardLeft + 20f, cardTop + 70f, hudTextPaint);
        }
    }
}
