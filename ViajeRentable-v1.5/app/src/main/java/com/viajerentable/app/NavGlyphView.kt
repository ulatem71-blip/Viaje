package com.viajerentable.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Small vector-like navigation glyphs drawn locally, without image assets or dependencies. */
class NavGlyphView(
    context: Context,
    private val glyph: Int
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val colors = VisualTheme.palette(context)
        paint.color = if (isSelected) colors.brand else colors.muted
        val scale = resources.displayMetrics.density
        canvas.save()
        canvas.scale(scale, scale)
        when (glyph) {
            0 -> drawHome(canvas)
            1 -> drawOffers(canvas)
            2 -> drawCosts(canvas)
            else -> drawDiagnostics(canvas)
        }
        canvas.restore()
    }

    private fun drawHome(canvas: Canvas) {
        val path = Path().apply {
            moveTo(2f, 11f)
            lineTo(12f, 3f)
            lineTo(22f, 11f)
            moveTo(5f, 10f)
            lineTo(5f, 21f)
            lineTo(19f, 21f)
            lineTo(19f, 10f)
            moveTo(10f, 21f)
            lineTo(10f, 15f)
            lineTo(14f, 15f)
            lineTo(14f, 21f)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawOffers(canvas: Canvas) {
        canvas.drawRoundRect(3f, 3f, 21f, 21f, 3f, 3f, paint)
        canvas.drawLine(7f, 8f, 17f, 8f, paint)
        canvas.drawLine(7f, 12f, 17f, 12f, paint)
        canvas.drawLine(7f, 16f, 14f, 16f, paint)
    }

    private fun drawCosts(canvas: Canvas) {
        canvas.drawCircle(12f, 12f, 9f, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 14f
        canvas.drawText("$", 8f, 17f, paint)
        paint.style = Paint.Style.STROKE
    }

    private fun drawDiagnostics(canvas: Canvas) {
        canvas.drawRoundRect(3f, 4f, 21f, 20f, 3f, 3f, paint)
        canvas.drawLine(7f, 9f, 10f, 12f, paint)
        canvas.drawLine(10f, 12f, 7f, 15f, paint)
        canvas.drawLine(13f, 15f, 17f, 15f, paint)
    }
}
