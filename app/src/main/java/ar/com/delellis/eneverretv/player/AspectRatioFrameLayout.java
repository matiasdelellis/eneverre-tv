package ar.com.delellis.eneverretv.player;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Fills its bounds with a black canvas and sizes its single child to the
 * requested aspect ratio, centered. The TV players are SurfaceViews: the
 * decoder stretches the picture to the surface, so the surface itself must
 * have the camera's aspect ratio or the image is deformed.
 */
public class AspectRatioFrameLayout extends FrameLayout {
    private static final float DEFAULT_ASPECT_RATIO = 16f / 9f;

    private float aspectRatio = DEFAULT_ASPECT_RATIO;

    public AspectRatioFrameLayout(@NonNull Context context) {
        super(context);
        init();
    }

    public AspectRatioFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AspectRatioFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setBackgroundColor(Color.BLACK);
    }

    /** Ratio as width / height; non-positive values are ignored. */
    public void setAspectRatio(float ratio) {
        if (ratio <= 0f) {
            return;
        }
        if (Math.abs(ratio - aspectRatio) < 0.0001f) {
            return;
        }
        aspectRatio = ratio;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (width > 0 && height > 0 && getChildCount() > 0) {
            float viewRatio = width / (float) height;
            int childWidth;
            int childHeight;
            if (viewRatio > aspectRatio) {
                // Container is wider than the picture: pillarbox.
                childHeight = height;
                childWidth = Math.round(height * aspectRatio);
            } else {
                // Container is taller than the picture: letterbox.
                childWidth = width;
                childHeight = Math.round(width / aspectRatio);
            }
            int childWidthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY);
            int childHeightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(childWidthSpec, childHeightSpec);
            }
        } else {
            for (int i = 0; i < getChildCount(); i++) {
                measureChild(getChildAt(i), widthMeasureSpec, heightMeasureSpec);
            }
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            int childLeft = (width - childWidth) / 2;
            int childTop = (height - childHeight) / 2;
            child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
        }
    }
}
