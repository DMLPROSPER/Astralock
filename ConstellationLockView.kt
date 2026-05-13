package com.example.astralock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.CycleInterpolator
import kotlin.math.sin
import kotlin.math.sqrt

class ConstellationLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Status { IDLE, WRONG, UNLOCKED }

    private data class Star(
        val id: Int,
        val xPct: Float,
        val yPct: Float,
        val size: Float,
        var x: Float = 0f,
        var y: Float = 0f
    )

    // 10 constellation stars
    private val stars = mutableListOf(
        Star(0, 0.28f, 0.22f, 1.4f),
        Star(1, 0.72f, 0.18f, 1.0f),
        Star(2, 0.50f, 0.38f, 1.6f),
        Star(3, 0.18f, 0.52f, 1.1f),
        Star(4, 0.82f, 0.44f, 1.3f),
        Star(5, 0.38f, 0.62f, 1.5f),
        Star(6, 0.65f, 0.68f, 0.9f),
        Star(7, 0.22f, 0.75f, 1.2f),
        Star(8, 0.78f, 0.78f, 1.0f),
        Star(9, 0.52f, 0.82f, 1.3f)
    )

    private val correctSequence = listOf(0, 4, 2, 7, 5)

    // Small background stars
    private data class BgStar(val x: Float, val y: Float, val size: Float, val phase: Float)
    private val bgStars = List(90) {
        BgStar(
            (Math.random() * 100).toFloat(),
            (Math.random() * 100).toFloat(),
            (Math.random() * 1.8f + 0.4f),
            (Math.random() * Math.PI * 2).toFloat()
        )
    }

    private val sequence = mutableListOf<Int>()
    private val lines = mutableListOf<Pair<Int, Int>>()
    private var currentStatus = Status.IDLE
    private var shakeOffset = 0f
    private var twinklePhase = 0f

    var onUnlockListener: (() -> Unit)? = null
    var onWrongListener: (() -> Unit)? = null
    var onResetListener: (() -> Unit)? = null

    fun getStatus() = currentStatus

    // Animators
    private val twinkleAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration = 3500
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            twinklePhase = it.animatedValue as Float
            invalidate()
        }
    }

    // Paints
    private val bgPaint = Paint()
    private val bgStarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val starCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val lineGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init {
        twinkleAnimator.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val starAreaTop = h * 0.28f
        val starAreaHeight = h * 0.60f
        stars.forEach { star ->
            star.x = star.xPct * w
            star.y = starAreaTop + star.yPct * starAreaHeight
        }
        val gradient = RadialGradient(
            w / 2f, h * 0.25f, w * 1.2f,
            intArrayOf(Color.rgb(12, 10, 50), Color.rgb(5, 4, 20), Color.rgb(1, 1, 8)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        bgPaint.shader = gradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(shakeOffset, 0f)

        // Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Twinkling background stars
        bgStars.forEach { s ->
            val phase = (twinklePhase + s.phase) % (2 * Math.PI).toFloat()
            val alpha = ((sin(phase.toDouble()) * 0.4 + 0.55) * 200).toInt().coerceIn(20, 200)
            bgStarPaint.alpha = alpha
            canvas.drawCircle(s.x / 100f * width, s.y / 100f * height, s.size, bgStarPaint)
        }

        // Lines between tapped stars
        lines.forEachIndexed { _, (fromId, toId) ->
            val from = stars[fromId]
            val to = stars[toId]
            val lineColor = if (currentStatus == Status.UNLOCKED)
                Color.argb(200, 160, 224, 255)
            else Color.argb(200, 120, 130, 255)
            linePaint.color = lineColor
            lineGlowPaint.color = Color.argb(50, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor))
            canvas.drawLine(from.x, from.y, to.x, to.y, lineGlowPaint)
            canvas.drawLine(from.x, from.y, to.x, to.y, linePaint)
        }

        // Draw constellation stars
        stars.forEach { star ->
            val active = sequence.contains(star.id)
            val isNext = !active &&
                    sequence.size < correctSequence.size &&
                    star.id == correctSequence[sequence.size] &&
                    currentStatus == Status.IDLE
            val unlocked = currentStatus == Status.UNLOCKED

            val baseRadius = star.size * 12f
            val radius = if (active || unlocked) baseRadius * 1.5f else baseRadius

            // Outer glow
            if (active || isNext || unlocked) {
                val glowRadius = radius * 3.5f
                val glowColor = when {
                    unlocked -> Color.argb(90, 100, 200, 255)
                    active -> Color.argb(90, 80, 80, 255)
                    else -> Color.argb(60, 140, 140, 255)
                }
                val glowGrad = RadialGradient(
                    star.x, star.y, glowRadius,
                    intArrayOf(glowColor, Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP
                )
                glowPaint.shader = glowGrad
                canvas.drawCircle(star.x, star.y, glowRadius, glowPaint)

                // Animated pulse ring
                val phase = (twinklePhase * 1.5f + star.id * 0.7f) % (2 * Math.PI).toFloat()
                val pulse = (sin(phase.toDouble()) * 0.35 + 1.5).toFloat()
                ringPaint.color = when {
                    unlocked -> Color.argb(80, 100, 200, 255)
                    active -> Color.argb(100, 100, 100, 255)
                    else -> Color.argb(70, 160, 160, 255)
                }
                canvas.drawCircle(star.x, star.y, radius * pulse, ringPaint)
            }

            // Star core gradient
            val coreColor = when {
                unlocked -> Color.argb(255, 160, 224, 255)
                active -> Color.argb(255, 180, 180, 255)
                isNext -> Color.argb(230, 210, 210, 255)
                else -> Color.argb(180, 210, 210, 255)
            }
            val coreGrad = RadialGradient(
                star.x - radius * 0.25f,
                star.y - radius * 0.25f,
                radius,
                intArrayOf(Color.WHITE, coreColor, Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            starCorePaint.shader = coreGrad
            canvas.drawCircle(star.x, star.y, radius, starCorePaint)
        }

        // Progress dots
        val dotCount = correctSequence.size
        val dotSpacing = 28f
        val startX = width / 2f - (dotCount - 1) * dotSpacing / 2f
        val dotY = height - 50f
        for (i in 0 until dotCount) {
            val filled = i < sequence.size
            dotPaint.color = when {
                filled && currentStatus == Status.WRONG -> Color.argb(220, 255, 80, 80)
                filled -> Color.argb(230, 120, 180, 255)
                else -> Color.argb(50, 255, 255, 255)
            }
            if (filled && currentStatus != Status.WRONG) {
                dotPaint.setShadowLayer(8f, 0f, 0f, Color.argb(150, 100, 150, 255))
            } else {
                dotPaint.clearShadowLayer()
            }
            canvas.drawCircle(startX + i * dotSpacing, dotY, 5.5f, dotPaint)
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (currentStatus == Status.UNLOCKED) return true
            handleTap(event.x, event.y)
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        val touchRadius = 55f
        val tapped = stars.minByOrNull { star ->
            val dx = star.x - x
            val dy = star.y - y
            sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        } ?: return

        val dist = sqrt(
            ((tapped.x - x) * (tapped.x - x) + (tapped.y - y) * (tapped.y - y)).toDouble()
        ).toFloat()

        if (dist > touchRadius) return
        if (sequence.contains(tapped.id)) return

        if (sequence.isNotEmpty()) lines.add(Pair(sequence.last(), tapped.id))
        sequence.add(tapped.id)

        val isCorrect = sequence == correctSequence.subList(0, sequence.size)

        if (!isCorrect) {
            currentStatus = Status.WRONG
            onWrongListener?.invoke()
            shakeAndReset()
            invalidate()
            return
        }

        if (sequence.size == correctSequence.size) {
            currentStatus = Status.UNLOCKED
            onUnlockListener?.invoke()
        }

        invalidate()
    }

    private fun shakeAndReset() {
        val shaker = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            interpolator = CycleInterpolator(3f)
            addUpdateListener {
                shakeOffset = (it.animatedValue as Float) * 22f
                invalidate()
            }
        }
        shaker.start()
        postDelayed({ reset() }, 850)
    }

    fun reset() {
        sequence.clear()
        lines.clear()
        currentStatus = Status.IDLE
        shakeOffset = 0f
        onResetListener?.invoke()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        twinkleAnimator.cancel()
    }
}
