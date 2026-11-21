package ar.com.delellis.eneverretv.player;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * A small, fixed-aspect camera wall: one or two columns of 16:9 tiles,
 * centered in the available space. GridLayout's weighted rows stretched
 * tiles to whatever height was left, so a two-camera wall became very tall
 * and the video had to be letterboxed. Sizing the tiles to the picture's
 * shape keeps every cell a camera-shaped rectangle.
 */
public class CameraGridLayout extends ViewGroup {
    private static final int GAP_DP = 12;
    private static final float TILE_ASPECT = 16f / 9f;

    public CameraGridLayout(Context context) {
        super(context);
    }

    public CameraGridLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CameraGridLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int count = getChildCount();
        if (count == 0) {
            setMeasuredDimension(width, height);
            return;
        }

        Layout layout = computeLayout(width, height, count);
        int childWidthSpec = MeasureSpec.makeMeasureSpec(layout.tileWidth, MeasureSpec.EXACTLY);
        int childHeightSpec = MeasureSpec.makeMeasureSpec(layout.tileHeight, MeasureSpec.EXACTLY);
        for (int i = 0; i < count; i++) {
            getChildAt(i).measure(childWidthSpec, childHeightSpec);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int count = getChildCount();
        if (count == 0) {
            return;
        }
        Layout layout = computeLayout(right - left, bottom - top, count);
        int contentWidth = right - left - getPaddingLeft() - getPaddingRight();
        int contentHeight = bottom - top - getPaddingTop() - getPaddingBottom();
        int startY = getPaddingTop() + (contentHeight - layout.totalHeight) / 2;
        for (int i = 0; i < count; i++) {
            int row = i / layout.columns;
            int column = i % layout.columns;
            int itemsInRow = Math.min(layout.columns, count - row * layout.columns);
            int rowWidth = itemsInRow * layout.tileWidth + (itemsInRow - 1) * layout.gap;
            int startX = getPaddingLeft() + (contentWidth - rowWidth) / 2;
            int childLeft = startX + column * (layout.tileWidth + layout.gap);
            int childTop = startY + row * (layout.tileHeight + layout.gap);

            View child = getChildAt(i);
            child.layout(childLeft, childTop,
                    childLeft + layout.tileWidth, childTop + layout.tileHeight);
        }
    }

    private Layout computeLayout(int width, int height, int count) {
        int gap = Math.round(GAP_DP * getResources().getDisplayMetrics().density);
        int columns = count <= 1 ? 1 : 2;
        int rows = (count + columns - 1) / columns;
        int availableWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int availableHeight = Math.max(0, height - getPaddingTop() - getPaddingBottom());

        int tileWidth = (availableWidth - gap * (columns - 1)) / columns;
        int tileHeight = Math.round(tileWidth / TILE_ASPECT);
        int totalHeight = rows * tileHeight + gap * (rows - 1);
        if (totalHeight > availableHeight) {
            tileHeight = (availableHeight - gap * (rows - 1)) / rows;
            tileWidth = Math.round(tileHeight * TILE_ASPECT);
            totalHeight = rows * tileHeight + gap * (rows - 1);
        }

        int totalWidth = columns * tileWidth + gap * (columns - 1);
        return new Layout(columns, gap, tileWidth, tileHeight, totalWidth, totalHeight);
    }

    private static final class Layout {
        final int columns;
        final int gap;
        final int tileWidth;
        final int tileHeight;
        final int totalWidth;
        final int totalHeight;

        Layout(int columns, int gap, int tileWidth, int tileHeight,
               int totalWidth, int totalHeight) {
            this.columns = columns;
            this.gap = gap;
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
            this.totalWidth = totalWidth;
            this.totalHeight = totalHeight;
        }
    }
}
