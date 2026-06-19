package com.ella.music.ui.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.animation.PathInterpolator;

public final class SquigglyProgress extends Drawable {
    private static final float TWO_PI = 6.2831855f;

    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path headPath = new Path();
    private final RectF glowRect = new RectF();

    private ValueAnimator heightAnimator;
    private boolean animate;
    private boolean transitionEnabled = true;
    private float heightFraction = 1f;
    private float lineAmplitude;
    private float phaseOffset;
    private float phaseSpeed;
    private float strokeWidth;
    private float waveLength;
    private long lastFrameTime = -1L;
    private int baseColor = 0xFFFFFFFF;
    private int baseAlpha = 255;

    public SquigglyProgress() {
        Paint.Cap cap = Paint.Cap.ROUND;
        progressPaint.setStrokeCap(cap);
        linePaint.setStrokeCap(cap);
        progressPaint.setStyle(Paint.Style.STROKE);
        linePaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStyle(Paint.Style.FILL);
        headPaint.setStyle(Paint.Style.FILL);
        updateColors(baseColor, baseAlpha);
    }

    @Override
    public void draw(Canvas canvas) {
        if (animate) {
            invalidateSelf();
            long now = SystemClock.uptimeMillis();
            if (lastFrameTime > 0L) {
                phaseOffset += ((now - lastFrameTime) / 1000f) * Math.max(1f, phaseSpeed);
            }
            lastFrameTime = now;
        }

        float width = getBounds().width();
        float height = getBounds().height();
        if (width <= 0f || height <= 0f) return;

        float progress = clamp(getLevel() / 10000f, 0f, 1f);
        float centerY = getBounds().centerY();
        float left = getBounds().left;
        float right = left + width;
        float progressX = left + width * progress;
        float visible = clamp(heightFraction, 0f, 1f);
        float safeStroke = Math.max(1f, strokeWidth);
        float headLength = Math.max(safeStroke * 9f, Math.max(1f, waveLength) * 1.18f);
        float tailLength = Math.max(headLength * 4.6f, safeStroke * 24f);
        float glowHeight = Math.max(safeStroke * 5.5f, lineAmplitude * 2.2f) * visible;
        float pulse = animate ? (0.92f + 0.08f * (float) Math.sin((phaseOffset / 280f) * TWO_PI)) : 1f;

        linePaint.setStrokeWidth(safeStroke);
        progressPaint.setStrokeWidth(safeStroke);

        canvas.drawLine(left, centerY, right, centerY, linePaint);

        if (progressX > left) {
            progressPaint.setShader(new LinearGradient(
                left,
                centerY,
                progressX,
                centerY,
                new int[] {
                    setAlphaComponent(baseColor, (int) (baseAlpha * 0.62f * visible)),
                    setAlphaComponent(baseColor, (int) (baseAlpha * 0.92f * visible)),
                    setAlphaComponent(baseColor, (int) (baseAlpha * visible))
                },
                new float[] {0f, 0.72f, 1f},
                Shader.TileMode.CLAMP
            ));
            canvas.drawLine(left, centerY, progressX, centerY, progressPaint);
            progressPaint.setShader(null);
        }

        if (progress <= 0f) return;

        float tailStart = Math.max(left, progressX - tailLength);
        float glowEnd = Math.min(right, progressX + headLength * 0.72f);
        glowPaint.setShader(new LinearGradient(
            tailStart,
            centerY,
            glowEnd,
            centerY,
            new int[] {
                setAlphaComponent(baseColor, 0),
                setAlphaComponent(baseColor, (int) (baseAlpha * 0.22f * visible)),
                setAlphaComponent(baseColor, (int) (baseAlpha * 0.70f * visible * pulse)),
                setAlphaComponent(baseColor, 0)
            },
            new float[] {0f, 0.54f, 0.86f, 1f},
            Shader.TileMode.CLAMP
        ));
        glowRect.set(tailStart, centerY - glowHeight * 0.5f, glowEnd, centerY + glowHeight * 0.5f);
        canvas.drawRoundRect(glowRect, glowHeight * 0.5f, glowHeight * 0.5f, glowPaint);

        float headTip = Math.min(right, progressX + headLength * 0.46f);
        float headBack = Math.max(left, progressX - headLength * 0.74f);
        float headHalfHeight = Math.max(safeStroke * 2.2f, lineAmplitude * 0.62f) * visible * pulse;
        headPaint.setShader(new LinearGradient(
            headBack,
            centerY,
            headTip,
            centerY,
            new int[] {
                setAlphaComponent(baseColor, 0),
                setAlphaComponent(baseColor, (int) (baseAlpha * 0.82f * visible)),
                setAlphaComponent(baseColor, baseAlpha)
            },
            new float[] {0f, 0.58f, 1f},
            Shader.TileMode.CLAMP
        ));
        headPath.rewind();
        headPath.moveTo(headTip, centerY);
        headPath.cubicTo(progressX, centerY - headHalfHeight, headBack, centerY - headHalfHeight * 0.72f, headBack, centerY);
        headPath.cubicTo(headBack, centerY + headHalfHeight * 0.72f, progressX, centerY + headHalfHeight, headTip, centerY);
        headPath.close();
        canvas.drawPath(headPath, headPaint);

        glowPaint.setShader(new RadialGradient(
            progressX,
            centerY,
            Math.max(headLength * 1.25f, glowHeight),
            new int[] {
                setAlphaComponent(baseColor, (int) (baseAlpha * 0.45f * visible * pulse)),
                setAlphaComponent(baseColor, (int) (baseAlpha * 0.12f * visible)),
                setAlphaComponent(baseColor, 0)
            },
            new float[] {0f, 0.45f, 1f},
            Shader.TileMode.CLAMP
        ));
        glowRect.set(
            progressX - headLength * 1.4f,
            centerY - glowHeight,
            progressX + headLength * 1.1f,
            centerY + glowHeight
        );
        canvas.drawOval(glowRect, glowPaint);
        glowPaint.setShader(null);
        headPaint.setShader(null);
    }

    @Override
    public int getAlpha() {
        return baseAlpha;
    }

    public boolean getAnimate() {
        return animate;
    }

    public float getLineAmplitude() {
        return lineAmplitude;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public float getPhaseSpeed() {
        return phaseSpeed;
    }

    public float getStrokeWidth() {
        return strokeWidth;
    }

    public boolean getTransitionEnabled() {
        return transitionEnabled;
    }

    public float getWaveLength() {
        return waveLength;
    }

    @Override
    protected boolean onLevelChange(int level) {
        return true;
    }

    @Override
    public void setAlpha(int alpha) {
        updateColors(baseColor, alpha);
    }

    public void setAnimate(boolean value) {
        if (animate == value) return;
        animate = value;
        if (value) {
            lastFrameTime = SystemClock.uptimeMillis();
        }

        if (heightAnimator != null) {
            heightAnimator.cancel();
        }
        ValueAnimator animator = ValueAnimator.ofFloat(heightFraction, animate ? 1f : 0f);
        if (animate) {
            animator.setStartDelay(60L);
            animator.setDuration(700L);
            animator.setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f));
        } else {
            animator.setDuration(420L);
            animator.setInterpolator(new PathInterpolator(0f, 0f, 0.2f, 1f));
        }
        animator.addUpdateListener(valueAnimator -> {
            heightFraction = (Float) valueAnimator.getAnimatedValue();
            invalidateSelf();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                heightAnimator = null;
            }
        });
        animator.start();
        heightAnimator = animator;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        progressPaint.setColorFilter(colorFilter);
        linePaint.setColorFilter(colorFilter);
        glowPaint.setColorFilter(colorFilter);
        headPaint.setColorFilter(colorFilter);
    }

    public void setLineAmplitude(float value) {
        lineAmplitude = value;
    }

    public void setPhaseSpeed(float value) {
        phaseSpeed = value;
    }

    public void setStrokeWidth(float value) {
        if (strokeWidth == value) return;
        strokeWidth = value;
        progressPaint.setStrokeWidth(value);
        linePaint.setStrokeWidth(value);
    }

    @Override
    public void setTint(int color) {
        updateColors(color, getAlpha());
    }

    @Override
    public void setTintList(ColorStateList tint) {
        if (tint == null) return;
        updateColors(tint.getDefaultColor(), getAlpha());
    }

    public void setTransitionEnabled(boolean value) {
        transitionEnabled = value;
        invalidateSelf();
    }

    public void setWaveLength(float value) {
        waveLength = value;
    }

    public void updateColors(int color, int alpha) {
        baseColor = color;
        baseAlpha = clampInt(alpha, 0, 255);
        progressPaint.setColor(setAlphaComponent(baseColor, baseAlpha));
        linePaint.setColor(setAlphaComponent(baseColor, (int) ((baseAlpha / 255f) * 74)));
        glowPaint.setColor(setAlphaComponent(baseColor, baseAlpha));
        headPaint.setColor(setAlphaComponent(baseColor, baseAlpha));
        invalidateSelf();
    }

    private static int setAlphaComponent(int color, int alpha) {
        return ((clampInt(alpha, 0, 255) & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
