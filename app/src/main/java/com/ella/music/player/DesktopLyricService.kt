package com.ella.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ella.music.R
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.sin

class DesktopLyricService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private var rootView: LinearLayout? = null
    private var lyricView: DesktopLyricView? = null
    private var controlsView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var locked = false
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var fontScale = 1f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        controllerFuture = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, PlaybackService::class.java))
        ).buildAsync().also { future ->
            future.addListener({ controller = runCatching { future.get() }.getOrNull() }, { it.run() })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW, ACTION_UPDATE -> showOrUpdate(intent)
            ACTION_HIDE -> stopSelf()
            ACTION_UNLOCK -> setLocked(false)
            ACTION_FONT_SMALLER -> updateFontScale(-0.08f)
            ACTION_FONT_LARGER -> updateFontScale(0.08f)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        rootView?.let { runCatching { windowManager.removeView(it) } }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        notificationManager.cancel(NOTIFICATION_ID)
        rootView = null
        lyricView = null
        controlsView = null
        layoutParams = null
        controller = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOrUpdate(intent: Intent) {
        if (!canDrawOverlay()) return
        if (rootView == null) addLyricView()
        lyricView?.setLyric(
            text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
            translation = intent.getStringExtra(EXTRA_TRANSLATION).orEmpty(),
            positionMs = intent.getLongExtra(EXTRA_POSITION, 0L),
            agent = intent.getStringExtra(EXTRA_AGENT).orEmpty(),
            isTtml = intent.getBooleanExtra(EXTRA_IS_TTML, false),
            backgroundText = intent.getStringExtra(EXTRA_BACKGROUND_TEXT).orEmpty(),
            backgroundTranslation = intent.getStringExtra(EXTRA_BACKGROUND_TRANSLATION).orEmpty(),
            wordTexts = intent.getStringArrayExtra(EXTRA_WORD_TEXTS)?.toList().orEmpty(),
            wordStarts = intent.getLongArrayExtra(EXTRA_WORD_STARTS) ?: LongArray(0),
            wordEnds = intent.getLongArrayExtra(EXTRA_WORD_ENDS) ?: LongArray(0),
            backgroundWordTexts = intent.getStringArrayExtra(EXTRA_BACKGROUND_WORD_TEXTS)?.toList().orEmpty(),
            backgroundWordStarts = intent.getLongArrayExtra(EXTRA_BACKGROUND_WORD_STARTS) ?: LongArray(0),
            backgroundWordEnds = intent.getLongArrayExtra(EXTRA_BACKGROUND_WORD_ENDS) ?: LongArray(0)
        )
    }

    private fun addLyricView() {
        val lyric = DesktopLyricView(this)
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(0))
            addControl("⏮", "上一首") { controller?.seekToPrevious() }
            addControl("⏯", "播放/暂停") { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
            addControl("⏭", "下一首") { controller?.seekToNext() }
            addControl("A-", "缩小歌词") { updateFontScale(-0.08f) }
            addControl("A+", "放大歌词") { updateFontScale(0.08f) }
            addControl("🔒", "锁定") { setLocked(true) }
            addControl("×", "关闭") { stopSelf() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(Color.argb(165, 12, 12, 16))
                setStroke(dp(1), Color.argb(70, 255, 255, 255))
            }
            elevation = dp(10).toFloat()
            addView(lyric, LinearLayout.LayoutParams(dp(560), dp(138)))
            addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            setOnTouchListener(::onDrag)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(96)
        }

        rootView = root
        lyricView = lyric
        controlsView = controls
        layoutParams = params
        windowManager.addView(root, params)
    }

    private fun LinearLayout.addControl(label: String, description: String, action: () -> Unit) {
        addView(TextView(context).apply {
            text = label
            contentDescription = description
            gravity = Gravity.CENTER
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(72, 255, 255, 255))
            }
            setPadding(0, 0, 0, dp(1))
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply {
            leftMargin = dp(4)
            rightMargin = dp(4)
        })
    }

    private fun updateFontScale(delta: Float) {
        fontScale = (fontScale + delta).coerceIn(0.72f, 1.35f)
        lyricView?.setFontScale(fontScale)
    }

    private fun setLocked(lock: Boolean) {
        locked = lock
        controlsView?.visibility = if (lock) View.GONE else View.VISIBLE
        val params = layoutParams ?: return
        params.flags = if (lock) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        rootView?.let { windowManager.updateViewLayout(it, params) }
        if (lock) postUnlockNotification() else notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun onDrag(view: View, event: MotionEvent): Boolean {
        if (locked) return false
        val params = layoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startX = params.x
                startY = params.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                params.x = startX + (event.rawX - downX).toInt()
                params.y = (startY + (event.rawY - downY).toInt()).coerceAtLeast(0)
                windowManager.updateViewLayout(view, params)
                return true
            }
        }
        return true
    }

    private fun postUnlockNotification() {
        ensureNotificationChannel()
        val intent = PendingIntent.getService(
            this,
            0,
            Intent(this, DesktopLyricService::class.java).setAction(ACTION_UNLOCK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        notificationManager.notify(
            NOTIFICATION_ID,
            builder
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle("桌面歌词已锁定")
                .setContentText("点击解除锁定")
                .setContentIntent(intent)
                .setOngoing(true)
                .setShowWhen(false)
                .build()
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "桌面歌词", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    private fun canDrawOverlay(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class DesktopLyricView(context: Context) : View(context) {
        private val pendingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(145, 255, 255, 255); textSize = 20f * resources.displayMetrics.scaledDensity; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        private val activePaint = Paint(pendingPaint).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
        private val glowPaint = Paint(activePaint).apply { color = Color.argb(130, 125, 205, 255); setShadowLayer(18f, 0f, 0f, color) }
        private val translationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 255, 255); textSize = 13f * resources.displayMetrics.scaledDensity; textAlign = Paint.Align.CENTER }
        private var text = "Ella Music"
        private var translation = ""
        private var agent = ""
        private var isTtml = false
        private var backgroundText = ""
        private var backgroundTranslation = ""
        private var fontScale = 1f
        private var positionMs = 0L
        private var words = emptyList<DesktopWord>()
        private var backgroundWords = emptyList<DesktopWord>()

        fun setFontScale(scale: Float) {
            fontScale = scale
            invalidate()
        }

        fun setLyric(
            text: String,
            translation: String,
            positionMs: Long,
            agent: String,
            isTtml: Boolean,
            backgroundText: String,
            backgroundTranslation: String,
            wordTexts: List<String>,
            wordStarts: LongArray,
            wordEnds: LongArray,
            backgroundWordTexts: List<String>,
            backgroundWordStarts: LongArray,
            backgroundWordEnds: LongArray
        ) {
            this.text = text.ifBlank { if (backgroundText.isBlank()) "♪" else "" }
            this.translation = translation
            this.agent = agent
            this.isTtml = isTtml
            this.backgroundText = backgroundText
            this.backgroundTranslation = backgroundTranslation
            this.positionMs = positionMs
            words = wordTexts.mapIndexedNotNull { index, word ->
                val start = wordStarts.getOrNull(index) ?: return@mapIndexedNotNull null
                val end = wordEnds.getOrNull(index) ?: return@mapIndexedNotNull null
                DesktopWord(word, start, end)
            }
            backgroundWords = backgroundWordTexts.mapIndexedNotNull { index, word ->
                val start = backgroundWordStarts.getOrNull(index) ?: return@mapIndexedNotNull null
                val end = backgroundWordEnds.getOrNull(index) ?: return@mapIndexedNotNull null
                DesktopWord(word, start, end)
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val hasBackground = backgroundText.isNotBlank() || backgroundWords.isNotEmpty()
            val primaryAlign = ttmlAlignForPrimary()
            val backgroundAlign = primaryAlign.opposite()
            if (hasBackground) {
                val primaryBaseline = height * 0.34f
                val backgroundBaseline = height * 0.62f
                drawLine(canvas, text, words, primaryAlign.anchorX(width), primaryBaseline, width * 0.82f, primaryAlign, true)
                drawLine(
                    canvas = canvas,
                    fallbackText = backgroundText.ifBlank { backgroundWords.joinToString("") { it.text } },
                    lineWords = backgroundWords,
                    anchorX = backgroundAlign.anchorX(width),
                    baseline = backgroundBaseline,
                    maxWidth = width * 0.82f,
                    align = backgroundAlign,
                    primary = false
                )
                val translationText = listOf(translation, backgroundTranslation).firstOrNull { it.isNotBlank() }.orEmpty()
                if (translationText.isNotBlank()) {
                    drawFittedText(canvas, translationText, width / 2f, height * 0.86f, width * 0.88f, AnchorAlign.Center, translationPaint)
                }
            } else {
                val baseline = height / 2f - if (translation.isBlank()) -5f else 10f
                drawLine(canvas, text, words, primaryAlign.anchorX(width), baseline, width * 0.9f, primaryAlign, true)
                if (translation.isNotBlank()) {
                    drawFittedText(canvas, translation, width / 2f, baseline + 25f * resources.displayMetrics.scaledDensity, width * 0.88f, AnchorAlign.Center, translationPaint)
                }
            }
        }

        private fun drawLine(canvas: Canvas, fallbackText: String, lineWords: List<DesktopWord>, anchorX: Float, baseline: Float, maxWidth: Float, align: AnchorAlign, primary: Boolean) {
            val oldPending = pendingPaint.textSize
            val oldActive = activePaint.textSize
            val oldGlow = glowPaint.textSize
            val targetSize = 20f * resources.displayMetrics.scaledDensity * fontScale
            pendingPaint.textSize = targetSize
            activePaint.textSize = targetSize
            glowPaint.textSize = targetSize
            if (lineWords.isEmpty()) {
                drawFittedText(canvas, fallbackText, anchorX, baseline, maxWidth, align, if (primary) activePaint else pendingPaint)
            } else {
                drawWords(canvas, anchorX, baseline, maxWidth, align, lineWords, primary)
            }
            pendingPaint.textSize = oldPending
            activePaint.textSize = oldActive
            glowPaint.textSize = oldGlow
        }

        private fun drawWords(canvas: Canvas, anchorX: Float, baseline: Float, maxWidth: Float, align: AnchorAlign, lineWords: List<DesktopWord>, primary: Boolean) {
            val originalPending = pendingPaint.textSize
            val originalActive = activePaint.textSize
            val originalGlow = glowPaint.textSize
            val originalPendingAlign = pendingPaint.textAlign
            val originalActiveAlign = activePaint.textAlign
            val originalGlowAlign = glowPaint.textAlign
            val displayWords = lineWords.mapNotNull { word -> word.text.trim().takeIf { it.isNotBlank() }?.let { word.copy(text = it) } }
            val widths = displayWords.map { pendingPaint.measureText(it.text) }
            val gap = 6f * resources.displayMetrics.density
            val totalWidth = widths.sum() + gap * (widths.size - 1).coerceAtLeast(0)
            val scale = (maxWidth / totalWidth.coerceAtLeast(1f)).coerceAtMost(1f)
            pendingPaint.textSize *= scale
            activePaint.textSize *= scale
            glowPaint.textSize *= scale
            pendingPaint.textAlign = Paint.Align.LEFT
            activePaint.textAlign = Paint.Align.LEFT
            glowPaint.textAlign = Paint.Align.LEFT
            val scaledWidths = displayWords.map { pendingPaint.measureText(it.text) }
            val scaledTotalWidth = scaledWidths.sum() + gap * (scaledWidths.size - 1).coerceAtLeast(0)
            var x = align.startX(anchorX, scaledTotalWidth)
            displayWords.forEachIndexed { index, word ->
                val token = word.text
                val isCurrent = positionMs in word.startMs..word.endMs
                val isPast = positionMs > word.endMs
                val paint = when {
                    isCurrent && primary -> activePaint
                    isPast -> Paint(activePaint).apply { color = Color.argb(210, 255, 255, 255) }
                    else -> pendingPaint
                }
                if (isCurrent && word.endMs - word.startMs >= 900L) {
                    val pulse = 0.42f + 0.34f * ((sin(positionMs / 145.0).toFloat() + 1f) / 2f)
                    glowPaint.alpha = (pulse * 255).toInt().coerceIn(0, 255)
                    canvas.drawText(token, x, baseline, glowPaint)
                }
                canvas.drawText(token, x, baseline, paint)
                x += scaledWidths[index] + gap
            }
            pendingPaint.textSize = originalPending
            activePaint.textSize = originalActive
            glowPaint.textSize = originalGlow
            pendingPaint.textAlign = originalPendingAlign
            activePaint.textAlign = originalActiveAlign
            glowPaint.textAlign = originalGlowAlign
        }

        private fun drawFittedText(canvas: Canvas, value: String, anchorX: Float, baseline: Float, maxWidth: Float, align: AnchorAlign, paint: Paint) {
            if (value.isBlank()) return
            val oldSize = paint.textSize
            val oldAlign = paint.textAlign
            val measured = paint.measureText(value)
            if (measured > maxWidth) paint.textSize = oldSize * (maxWidth / measured).coerceIn(0.58f, 1f)
            paint.textAlign = align.paintAlign
            canvas.drawText(value, anchorX, baseline, paint)
            paint.textSize = oldSize
            paint.textAlign = oldAlign
        }

        private fun ttmlAlignForPrimary(): AnchorAlign {
            if (!isTtml || agent.isBlank()) return AnchorAlign.Center
            return if (agent.equals("v2", ignoreCase = true)) AnchorAlign.Right else AnchorAlign.Left
        }
    }

    private data class DesktopWord(val text: String, val startMs: Long, val endMs: Long)

    private enum class AnchorAlign(val paintAlign: Paint.Align) {
        Left(Paint.Align.LEFT),
        Center(Paint.Align.CENTER),
        Right(Paint.Align.RIGHT);

        fun anchorX(width: Int): Float = when (this) {
            Left -> width * 0.08f
            Center -> width / 2f
            Right -> width * 0.92f
        }

        fun startX(anchorX: Float, lineWidth: Float): Float = when (this) {
            Left -> anchorX
            Center -> anchorX - lineWidth / 2f
            Right -> anchorX - lineWidth
        }

        fun opposite(): AnchorAlign = when (this) {
            Left -> Right
            Right -> Left
            Center -> Center
        }
    }

    companion object {
        const val ACTION_SHOW = "com.ella.music.action.SHOW_DESKTOP_LYRIC"
        const val ACTION_UPDATE = "com.ella.music.action.UPDATE_DESKTOP_LYRIC"
        const val ACTION_HIDE = "com.ella.music.action.HIDE_DESKTOP_LYRIC"
        const val ACTION_UNLOCK = "com.ella.music.action.UNLOCK_DESKTOP_LYRIC"
        const val ACTION_FONT_SMALLER = "com.ella.music.action.DESKTOP_LYRIC_FONT_SMALLER"
        const val ACTION_FONT_LARGER = "com.ella.music.action.DESKTOP_LYRIC_FONT_LARGER"
        const val EXTRA_TEXT = "text"
        const val EXTRA_TRANSLATION = "translation"
        const val EXTRA_POSITION = "position"
        const val EXTRA_AGENT = "agent"
        const val EXTRA_IS_TTML = "is_ttml"
        const val EXTRA_BACKGROUND_TEXT = "background_text"
        const val EXTRA_BACKGROUND_TRANSLATION = "background_translation"
        const val EXTRA_WORD_TEXTS = "word_texts"
        const val EXTRA_WORD_STARTS = "word_starts"
        const val EXTRA_WORD_ENDS = "word_ends"
        const val EXTRA_BACKGROUND_WORD_TEXTS = "background_word_texts"
        const val EXTRA_BACKGROUND_WORD_STARTS = "background_word_starts"
        const val EXTRA_BACKGROUND_WORD_ENDS = "background_word_ends"
        private const val CHANNEL_ID = "ella_desktop_lyric"
        private const val NOTIFICATION_ID = 0x454c4459
    }
}
