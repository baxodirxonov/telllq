package uz.sensoryordamchi

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Ekranning ishlaydigan (chap) qismida virtual touchpad ko'rsatadi.
 * Touchpad orqali kursor butun ekran bo'ylab (o'lik qism ham) yuritiladi,
 * bosish va scroll esa kursor turgan joyga sun'iy jest sifatida yuboriladi.
 */
class TouchpadService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var cursor: CursorView? = null
    private var panel: View? = null
    private var bubble: View? = null

    private var screenW = 0
    private var screenH = 0

    // Kursorning ekrandagi joyi (piksel)
    private var cx = 0f
    private var cy = 0f

    // Barmoq 1 px yursa, kursor shuncha px yuradi. Kerak bo'lsa o'zgartiring.
    private val gain = 2.2f

    // Touchpad holati
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var moved = false
    private var multi = false
    private var longFired = false
    private var lastMultiY = 0f
    private var scrollAcc = 0f
    private var scrollBusy = false

    private val longPressRunnable = Runnable {
        if (!moved && !multi) {
            longFired = true
            sendTap(700)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        measureScreen()
        cx = screenW * 0.4f
        cy = screenH * 0.5f
        buildUi()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        measureScreen()
        cx = cx.coerceIn(0f, screenW - 1f)
        cy = cy.coerceIn(0f, screenH - 1f)
        buildUi()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        removeUi()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        removeUi()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- UI

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun measureScreen() {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        screenW = dm.widthPixels
        screenH = dm.heightPixels
    }

    private fun removeUi() {
        listOf(cursor, panel, bubble).forEach { v ->
            if (v != null) {
                try {
                    wm.removeView(v)
                } catch (_: Exception) {
                }
            }
        }
        cursor = null
        panel = null
        bubble = null
    }

    private fun buildUi() {
        removeUi()

        // 1) Kursor qatlami: butun ekran, tegishni o'tkazib yuboradi
        val cv = CursorView(this, dp(12).toFloat())
        val cursorLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(cv, cursorLp)
        cv.moveTo(cx, cy)
        cursor = cv

        // 2) Touchpad paneli (chap pastda)
        val panelView = buildPanel()
        val panelWidth = min((screenW * 0.62f).toInt(), dp(360))
        val panelLp = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = dp(6)
            y = dp(6)
        }
        wm.addView(panelView, panelLp)
        panel = panelView

        // 3) Yig'ilgan holat uchun kichik doira tugma (boshida yashirin, ya'ni qo'shilmagan)
        bubble = buildBubble()
    }

    private fun buildPanel(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                setColor(0xAA222222.toInt())
                cornerRadius = dp(14).toFloat()
            }
        }

        fun row(vararg buttons: Button): LinearLayout {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.forEach { b ->
                r.addView(
                    b,
                    LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                        setMargins(dp(2), dp(2), dp(2), dp(2))
                    }
                )
            }
            return r
        }

        val back = actionButton("Orqaga") { performGlobalAction(GLOBAL_ACTION_BACK) }
        val home = actionButton("Uy") { performGlobalAction(GLOBAL_ACTION_HOME) }
        val recents = actionButton("Ilovalar") { performGlobalAction(GLOBAL_ACTION_RECENTS) }
        val up = actionButton("▲ Tepa") { scrollPage(towardTop = true) }
        val down = actionButton("▼ Past") { scrollPage(towardTop = false) }
        val collapse = actionButton("Yig'ish") { showBubble() }

        root.addView(row(back, home, recents))
        root.addView(row(up, down, collapse))

        val pad = TextView(this).apply {
            text = "Shu yerda barmoq bilan suring\nBosish: bir marta tegib oling\nScroll: ikki barmoq bilan suring"
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0x33FFFFFF)
                setStroke(dp(2), 0x88FFFFFF.toInt())
                cornerRadius = dp(12).toFloat()
            }
            setOnTouchListener { _, e ->
                handlePad(e)
                true
            }
        }
        val padHeight = min((screenH * 0.28f).toInt(), dp(240))
        root.addView(
            pad,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, padHeight).apply {
                setMargins(dp(2), dp(4), dp(2), dp(2))
            }
        )
        return root
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener { onClick() }
        }

    private fun buildBubble(): View =
        TextView(this).apply {
            text = "◎"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xAA222222.toInt())
            }
            setOnClickListener { showPanel() }
        }

    private fun showBubble() {
        panel?.let { p ->
            try {
                wm.removeView(p)
            } catch (_: Exception) {
            }
        }
        panel = null
        val b = bubble ?: return
        val size = dp(52)
        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = dp(6)
            y = dp(6)
        }
        try {
            wm.addView(b, lp)
        } catch (_: Exception) {
        }
    }

    private fun showPanel() {
        bubble?.let { b ->
            try {
                wm.removeView(b)
            } catch (_: Exception) {
            }
        }
        val panelView = buildPanel()
        val lp = WindowManager.LayoutParams(
            min((screenW * 0.62f).toInt(), dp(360)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = dp(6)
            y = dp(6)
        }
        wm.addView(panelView, lp)
        panel = panelView
    }

    // ---------------------------------------------------------- Touchpad

    private fun avgY(e: MotionEvent): Float = (e.getY(0) + e.getY(1)) / 2f

    private fun handlePad(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x
                lastY = e.y
                downX = e.x
                downY = e.y
                downTime = e.eventTime
                moved = false
                multi = false
                longFired = false
                scrollAcc = 0f
                handler.postDelayed(longPressRunnable, 600)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (e.pointerCount >= 2) {
                    multi = true
                    handler.removeCallbacks(longPressRunnable)
                    lastMultiY = avgY(e)
                    scrollAcc = 0f
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount >= 2) {
                    // Ikki barmoq: scroll
                    multi = true
                    val y = avgY(e)
                    scrollAcc += y - lastMultiY
                    lastMultiY = y
                    if (abs(scrollAcc) > dp(24)) {
                        // Barmoq yuqoriga yursa, sahifa pastga (ko'proq kontent) ketadi
                        scrollPage(towardTop = scrollAcc > 0)
                        scrollAcc = 0f
                    }
                } else if (!multi) {
                    // Bir barmoq: kursorni yurgizish
                    val dx = e.x - lastX
                    val dy = e.y - lastY
                    lastX = e.x
                    lastY = e.y
                    cx = (cx + dx * gain).coerceIn(0f, screenW - 1f)
                    cy = (cy + dy * gain).coerceIn(0f, screenH - 1f)
                    cursor?.moveTo(cx, cy)
                    if (hypot(e.x - downX, e.y - downY) > dp(8)) {
                        moved = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (!moved && !multi && !longFired && e.eventTime - downTime < 350) {
                    sendTap(50)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
            }
        }
    }

    // ------------------------------------------------------------ Jestlar

    /** Kursor turgan nuqtani bosadi (durationMs katta bo'lsa uzoq bosish). */
    private fun sendTap(durationMs: Long) {
        val path = Path().apply { moveTo(cx, cy) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
        cursor?.flash()
    }

    /**
     * Kursor turgan joyda vertikal surish.
     * towardTop = true: sahifaning yuqori qismini ko'rish (barmoq pastga suriladi).
     */
    private fun scrollPage(towardTop: Boolean) {
        if (scrollBusy) return
        val half = screenH * 0.18f
        var y0 = if (towardTop) cy - half else cy + half
        var y1 = if (towardTop) cy + half else cy - half
        y0 = y0.coerceIn(30f, screenH - 30f)
        y1 = y1.coerceIn(30f, screenH - 30f)
        if (abs(y1 - y0) < 20f) return

        val path = Path().apply {
            moveTo(cx, y0)
            lineTo(cx, y1)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
            .build()

        scrollBusy = true
        handler.postDelayed({ scrollBusy = false }, 700)
        dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    scrollBusy = false
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    scrollBusy = false
                }
            },
            null
        )
    }
}
