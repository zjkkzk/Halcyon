package com.ella.music.ui.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * 波浪形进度条 Drawable（逆向自系统 UI SquigglyProgress）
 *
 * 绘制原理：
 * 1. 用贝塞尔曲线(cubicTo)绘制正弦波形路径
 * 2. 已播放部分：clipRect 裁剪 + wavePaint（不透明）绘制波浪
 * 3. 未播放部分：clipRect 裁剪 + linePaint（alpha=77/255 半透明）绘制波浪或直线
 * 4. 起点处绘制一个跳动的圆点（用余弦函数计算 Y 偏移）
 * 5. transitionEnabled=true 时，未播放部分也画波浪（彗星尾巴效果）
 * 6. animate=true 时，波浪相位随时间推移，产生流动动画
 */
class SquigglyProgress : Drawable() {

    var animate = false
        private set
    var heightFraction = 1f
    var lastFrameTime = -1L
    var lineAmplitude = 0f
    var phaseOffset = 0f
    var phaseSpeed = 0f
    var strokeWidth = 0f
        set(value) {
            if (field == value) return
            field = value
            wavePaint.strokeWidth = value
            linePaint.strokeWidth = value
        }
    var transitionEnabled = true
    var waveLength = 0f

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
        alpha = 77  // 半透明，产生渐隐效果
    }
    private val path = Path()
    private var heightAnimator: ValueAnimator? = null

    private val transitionPeriods = 1.5f
    private val minWaveEndpoint = 0.2f
    private val matchedWaveEndpoint = 0.6f

    override fun draw(canvas: Canvas) {
        drawTraced(canvas)
    }

    private fun drawTraced(canvas: Canvas) {
        // 相位动画：波浪随时间流动
        if (animate) {
            invalidateSelf()
            val now = SystemClock.uptimeMillis()
            phaseOffset = (((now - lastFrameTime) / 1000f) * phaseSpeed + phaseOffset) % waveLength
            lastFrameTime = now
        }

        // 计算播放进度（level 范围 0~1）
        var level = level / 10000f
        val width = bounds.width().toFloat()
        val progressX = width * level

        // 过渡区域映射：让低进度时波浪不展开到底
        if (transitionEnabled && level <= matchedWaveEndpoint) {
            level = lerp(minWaveEndpoint, matchedWaveEndpoint, lerpInv(0f, matchedWaveEndpoint, level))
        }
        val levelWidth = level * width

        // 振幅计算函数
        val computeAmplitude = { x: Float, sign: Float ->
            if (transitionEnabled) {
                val waveLen = waveLength * transitionPeriods
                val halfWave = waveLen / 2f
                val fadeFactor = lerpInvSat(levelWidth + halfWave, levelWidth - halfWave, x)
                lineAmplitude * sign * heightFraction * fadeFactor
            } else {
                lineAmplitude * sign * heightFraction
            }
        }

        // 用贝塞尔曲线绘制波浪路径
        val startOffset = -phaseOffset - waveLength / 2f
        val drawEnd = if (transitionEnabled) width else levelWidth
        val halfWave = waveLength / 2f

        path.rewind()
        path.moveTo(startOffset, 0f)
        var sign = 1f
        var prevAmplitude = computeAmplitude(startOffset, sign)
        var x = startOffset
        while (x < drawEnd) {
            sign = -sign
            val midX = x + halfWave
            val ctrlX = halfWave / 2f + x
            val nextAmplitude = computeAmplitude(midX, sign)
            path.cubicTo(ctrlX, prevAmplitude, ctrlX, nextAmplitude, midX, nextAmplitude)
            prevAmplitude = nextAmplitude
            x = midX
        }

        val clipHalf = lineAmplitude + strokeWidth

        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.centerY().toFloat())

        // 已播放部分：实色波浪
        canvas.save()
        canvas.clipRect(0f, -clipHalf, progressX, clipHalf)
        canvas.drawPath(path, wavePaint)
        canvas.restore()

        // 未播放部分：半透明波浪（彗星尾巴）或直线
        if (transitionEnabled) {
            canvas.save()
            canvas.clipRect(progressX, -clipHalf, width, clipHalf)
            canvas.drawPath(path, linePaint)
            canvas.restore()
        } else {
            canvas.drawLine(progressX, 0f, width, 0f, linePaint)
        }

        // 起点圆点：随波浪上下跳动
        val pointY = cos(kotlin.math.abs(startOffset) / waveLength * 2 * Math.PI).toFloat() *
            lineAmplitude * heightFraction
        canvas.drawPoint(0f, pointY, wavePaint)

        canvas.restore()
    }

    override fun getAlpha(): Int = wavePaint.alpha

    override fun getOpacity(): Int = -3  // TRANSLUCENT

    override fun setAlpha(alpha: Int) {
        wavePaint.color = setAlpha(wavePaint.color, alpha)
        linePaint.color = setAlpha(wavePaint.color, ((alpha / 255f) * 77).toInt())
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        wavePaint.colorFilter = colorFilter
        linePaint.colorFilter = colorFilter
    }

    override fun onLevelChange(level: Int): Boolean = animate

    fun setAnimate(value: Boolean) {
        if (animate == value) return
        animate = value
        if (value) lastFrameTime = SystemClock.uptimeMillis()

        heightAnimator?.cancel()
        val target = if (animate) 1f else 0f
        val animator = ValueAnimator.ofFloat(heightFraction, target).apply {
            if (animate) {
                startDelay = 60L
                duration = 800L
                interpolator = AccelerateDecelerateInterpolator()
            } else {
                duration = 550L
                interpolator = DecelerateInterpolator()
            }
            addUpdateListener {
                heightFraction = it.animatedValue as Float
                invalidateSelf()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    heightAnimator = null
                }
            })
            start()
        }
        heightAnimator = animator
    }

    override fun setTint(color: Int) {
        updateColors(color, alpha)
    }

    fun updateColors(color: Int, alpha: Int) {
        wavePaint.color = setAlpha(color, alpha)
        linePaint.color = setAlpha(color, ((alpha / 255f) * 77).toInt())
    }

    companion object {
        /** 设置颜色的 alpha 分量 */
        private fun setAlpha(color: Int, alpha: Int): Int =
            (alpha and 0xFF shl 24) or (color and 0x00FFFFFF)

        /** 线性插值 */
        private fun lerp(start: Float, end: Float, fraction: Float): Float =
            start + (end - start) * fraction.coerceIn(0f, 1f)

        /** 线性插值的逆函数 */
        private fun lerpInv(start: Float, end: Float, value: Float): Float {
            if (start == end) return 0f
            return ((value - start) / (end - start)).coerceIn(0f, 1f)
        }

        /** 饱和线性插值逆函数 */
        private fun lerpInvSat(start: Float, end: Float, value: Float): Float {
            val clamped = value.coerceIn(min(start, end), max(start, end))
            return lerpInv(start, end, clamped)
        }
    }
}
