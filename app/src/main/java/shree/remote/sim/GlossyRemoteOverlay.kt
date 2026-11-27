// Important instructions:
// - Render a glossy-shine floating TV remote with configurable opacity, D-Pad Buttons mode, and Gesture Trackpad mode.
// - Support smooth dragging anywhere on screen and dispatch Up, Down, Left, Right, OK, Long-OK, and Back actions.

package shree.remote.sim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class GlossyRemoteOverlay(
    private val ctx: Context,
    private val prefs: RemotePrefs,
    private val onAction: (RemoteAction) -> Unit,
    private val onToggleMode: () -> Unit,
    private val onHideOverlay: () -> Unit
) {
    enum class RemoteAction { UP, DOWN, LEFT, RIGHT, OK, LONG_OK, BACK }

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val density = ctx.resources.displayMetrics.density

    private fun dp(v: Int): Int = (v * density).toInt()
    private fun dpF(v: Float): Float = v * density

    fun isShowing(): Boolean = rootView != null

    fun show() {
        hide()
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.overlayX
            y = prefs.overlayY
        }

        val container = buildGlossyShell()
        params = lp
        rootView = container
        try {
            wm.addView(container, lp)
        } catch (_: Exception) {
            rootView = null
        }
    }

    fun hide() {
        rootView?.let { v ->
            try { wm.removeView(v) } catch (_: Exception) {}
        }
        rootView = null
        params = null
    }

    private fun buildGlossyShell(): View {
        val shell = object : LinearLayout(ctx) {
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
            private val rect = RectF()
            private val sheenRect = RectF()

            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0f || h <= 0f) return
                val stroke = dpF(2.2f)
                val half = stroke / 2f
                val r = dpF(24f)

                rect.set(half, half, w - half, h - half)
                fillPaint.shader = LinearGradient(
                    0f, 0f, w * 0.4f, h,
                    intArrayOf(Color.rgb(24, 34, 68), Color.rgb(12, 17, 36)),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(rect, r, r, fillPaint)

                sheenRect.set(stroke, stroke, w - stroke, h * 0.36f)
                sheenPaint.shader = LinearGradient(
                    0f, 0f, 0f, h * 0.36f,
                    intArrayOf(Color.argb(95, 255, 255, 255), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(sheenRect, r, r, sheenPaint)

                rimPaint.strokeWidth = stroke
                rimPaint.shader = LinearGradient(
                    0f, 0f, w, h,
                    intArrayOf(Color.WHITE, Color.rgb(72, 198, 255), Color.rgb(168, 92, 255), Color.WHITE),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(rect, r, r, rimPaint)
                super.onDraw(canvas)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setWillNotDraw(false)
            alpha = prefs.overlayOpacity / 100f
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }

        val topHandle = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        val dragGrip = TextView(ctx).apply {
            text = "⋮⋮ TV REMOTE"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(185, 220, 255))
        }
        val isGesture = prefs.overlayMode == RemotePrefs.MODE_GESTURE
        val modeBadge = makeGlossyChip(if (isGesture) "Swipe" else "D-Pad", accent = true) { onToggleMode() }
        val closeChip = makeGlossyChip("✕", accent = false) { onHideOverlay() }
        topHandle.addView(dragGrip, LinearLayout.LayoutParams(0, -2, 1f))
        topHandle.addView(modeBadge, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        topHandle.addView(closeChip, LinearLayout.LayoutParams(-2, -2))
        attachDragListener(topHandle)
        shell.addView(topHandle, LinearLayout.LayoutParams(-1, -2))

        if (isGesture) shell.addView(buildGlossyGesturePad()) else shell.addView(buildGlossyDpadButtons())

        val backBtn = makeGlossyKey("‹  BACK", dp(156), dp(36), primary = false) { onAction(RemoteAction.BACK) }
        shell.addView(backBtn, LinearLayout.LayoutParams(dp(156), dp(36)).apply { topMargin = dp(8) })
        return shell
    }
    private fun buildGlossyDpadButtons(): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val btnSize = dp(48)
        val gap = dp(6)

        val upBtn = makeGlossyKey("▲", btnSize, btnSize, primary = false) { onAction(RemoteAction.UP) }
        box.addView(upBtn, LinearLayout.LayoutParams(btnSize, btnSize).apply { bottomMargin = gap })

        val midRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val leftBtn = makeGlossyKey("◀", btnSize, btnSize, primary = false) { onAction(RemoteAction.LEFT) }
        val okBtn = makeGlossyKey("OK", btnSize, btnSize, primary = true) { onAction(RemoteAction.OK) }.apply {
            setOnLongClickListener {
                onAction(RemoteAction.LONG_OK)
                true
            }
        }
        val rightBtn = makeGlossyKey("▶", btnSize, btnSize, primary = false) { onAction(RemoteAction.RIGHT) }

        midRow.addView(leftBtn, LinearLayout.LayoutParams(btnSize, btnSize).apply { rightMargin = gap })
        midRow.addView(okBtn, LinearLayout.LayoutParams(btnSize, btnSize).apply { rightMargin = gap })
        midRow.addView(rightBtn, LinearLayout.LayoutParams(btnSize, btnSize))
        box.addView(midRow)

        val downBtn = makeGlossyKey("▼", btnSize, btnSize, primary = false) { onAction(RemoteAction.DOWN) }
        box.addView(downBtn, LinearLayout.LayoutParams(btnSize, btnSize).apply { topMargin = gap })
        return box
    }

    private fun buildGlossyGesturePad(): View {
        val padSize = dp(156)
        val swipeMin = ViewConfiguration.get(ctx).scaledTouchSlop * 2.0f
        var startX = 0f
        var startY = 0f
        var swiped = false

        val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!swiped) onAction(RemoteAction.OK)
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                if (!swiped) onAction(RemoteAction.LONG_OK)
            }
        })

        return FrameLayout(ctx).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(34, 52, 98), Color.rgb(18, 28, 58))
            ).apply {
                cornerRadius = dpF(18f)
                setStroke(dp(1), Color.rgb(92, 205, 255))
            }
            addView(TextView(ctx).apply {
                text = "▲\n◀   SWIPE / TAP   ▶\n▼\nHold = Menu"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(215, 236, 255))
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

            setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { startX = ev.x; startY = ev.y; swiped = false }
                    MotionEvent.ACTION_MOVE -> {
                        if (!swiped) {
                            val dx = ev.x - startX
                            val dy = ev.y - startY
                            if (abs(dx) >= swipeMin || abs(dy) >= swipeMin) {
                                swiped = true
                                val act = if (abs(dx) > abs(dy)) {
                                    if (dx > 0f) RemoteAction.RIGHT else RemoteAction.LEFT
                                } else {
                                    if (dy > 0f) RemoteAction.DOWN else RemoteAction.UP
                                }
                                onAction(act)
                            }
                        }
                    }
                }
                detector.onTouchEvent(ev)
                true
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(padSize, padSize)
        }
    }

    private fun makeGlossyKey(label: String, w: Int, h: Int, primary: Boolean, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label
            textSize = if (primary) 13f else 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (primary) intArrayOf(Color.rgb(58, 152, 255), Color.rgb(32, 96, 218))
                else intArrayOf(Color.rgb(44, 62, 112), Color.rgb(24, 34, 68))
            ).apply {
                cornerRadius = dpF(14f)
                setStroke(dp(1), if (primary) Color.WHITE else Color.argb(140, 175, 215, 255))
            }
            setOnClickListener { onClick() }
        }

    private fun makeGlossyChip(label: String, accent: Boolean, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                setColor(if (accent) Color.rgb(48, 132, 238) else Color.rgb(58, 42, 76))
                cornerRadius = dpF(10f)
                setStroke(dp(1), Color.argb(140, 255, 255, 255))
            }
            setOnClickListener { onClick() }
        }

    private fun attachDragListener(handle: View) {
        var initX = 0; var initY = 0; var touchX = 0f; var touchY = 0f
        handle.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            val rv = rootView ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { initX = lp.x; initY = lp.y; touchX = event.rawX; touchY = event.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = initX + (event.rawX - touchX).toInt()
                    lp.y = initY + (event.rawY - touchY).toInt()
                    try { wm.updateViewLayout(rv, lp) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> { prefs.overlayX = lp.x; prefs.overlayY = lp.y; true }
                else -> false
            }
        }
    }
}
