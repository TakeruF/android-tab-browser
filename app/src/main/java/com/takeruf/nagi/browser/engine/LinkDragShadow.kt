package com.takeruf.nagi.browser.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.composables.icons.lucide.R as LucideR
import com.takeruf.nagi.ui.theme.NagiShapes
import kotlin.math.roundToInt

internal data class LinkDragPreviewColors(val background: Int, val content: Int, val outline: Int, val accent: Int)

/** Draw the rounded card and its shadow inside a transparent native drag surface. */
internal class LinkDragShadow(context: Context, host: String, colors: LinkDragPreviewColors?,
    private val fitCanvas: Boolean = false) : View.DragShadowBuilder() {
    private val density = context.resources.displayMetrics.density
    private val margin = (12 * density).roundToInt()
    private val radius = NagiShapes.Radius.value * density
    private val palette = colors ?: LinkDragPreviewColors(
        themeColor(context, android.R.attr.colorBackground),
        themeColor(context, android.R.attr.textColorPrimary),
        themeColor(context, android.R.attr.colorControlNormal),
        themeColor(context, android.R.attr.colorAccent),
    )
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.background
        setShadowLayer(6 * density, 0f, 2 * density, 0x30000000)
    }
    private val preview = TextView(context).apply {
        text = host
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(palette.content)
        minHeight = (44 * density).roundToInt()
        setPadding((14 * density).roundToInt(), (10 * density).roundToInt(),
            (16 * density).roundToInt(), (10 * density).roundToInt())
        background = GradientDrawable().apply {
            cornerRadius = radius
            setColor(palette.background)
            setStroke(density.roundToInt().coerceAtLeast(1), palette.outline)
        }
        val icon = ContextCompat.getDrawable(context, LucideR.drawable.lucide_ic_link)?.mutate()?.apply {
            setTint(palette.accent)
            setBounds(0, 0, (18 * density).roundToInt(), (18 * density).roundToInt())
        }
        setCompoundDrawables(icon, null, null, null)
        compoundDrawablePadding = (10 * density).roundToInt()
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        measure(View.MeasureSpec.makeMeasureSpec((260 * density).roundToInt(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        layout(0, 0, measuredWidth, measuredHeight)
    }

    override fun onProvideShadowMetrics(size: Point, touch: Point) {
        size.set(preview.width + margin * 2, preview.height + margin * 2)
        // Keep the label above the finger without changing the actual drop position.
        touch.set(size.x / 2, size.y - margin)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val saved = canvas.save()
        if (fitCanvas) {
            // WebView owns the original drag surface size; updates must fit that surface.
            val width = preview.width + margin * 2
            val height = preview.height + margin * 2
            val scale = minOf(1f, canvas.width.toFloat() / width, canvas.height.toFloat() / height)
            canvas.translate((canvas.width - width * scale) / 2f, (canvas.height - height * scale) / 2f)
            canvas.scale(scale, scale)
        }
        canvas.drawRoundRect(margin.toFloat(), margin.toFloat(), (margin + preview.width).toFloat(),
            (margin + preview.height).toFloat(), radius, radius, shadowPaint)
        canvas.translate(margin.toFloat(), margin.toFloat())
        preview.draw(canvas)
        canvas.restoreToCount(saved)
    }

    private fun themeColor(context: Context, attribute: Int): Int =
        context.obtainStyledAttributes(intArrayOf(attribute)).use { it.getColor(0, Color.TRANSPARENT) }
}
