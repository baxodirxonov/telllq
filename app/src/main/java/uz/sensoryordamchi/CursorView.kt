package uz.sensoryordamchi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Butun ekran ustida kursorni (qizil aylana) chizadigan shaffof view. */
class CursorView(context: Context, private val radius: Float) : View(context) {

    private var px = 0f
    private var py = 0f
    private var flashUntil = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFF3B30.toInt()
        style = Paint.Style.FILL
    }
    private val flashFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC34C759.toInt()
        style = Paint.Style.FILL
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = radius / 4f
    }

    fun moveTo(x: Float, y: Float) {
        px = x
        py = y
        invalidate()
    }

    /** Bosish bo'lganini ko'rsatish uchun kursor qisqa vaqt yashil bo'ladi. */
    fun flash() {
        flashUntil = System.currentTimeMillis() + 150
        invalidate()
        postDelayed({ invalidate() }, 170)
    }

    override fun onDraw(canvas: Canvas) {
        val paint = if (System.currentTimeMillis() < flashUntil) flashFill else fill
        canvas.drawCircle(px, py, radius, paint)
        canvas.drawCircle(px, py, radius, ring)
    }
}
