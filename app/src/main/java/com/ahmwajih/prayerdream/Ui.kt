package com.ahmwajih.prayerdream

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

object Ui {
    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    fun glass(ctx: Context, fill: Int = 0x66000000, stroke: Int = 0x33FFFFFF, radius: Int = 18) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(ctx, radius).toFloat()
            setStroke(dp(ctx, 1), stroke)
        }

    fun menuButton(ctx: Context, text: String, accent: Int, onClick: () -> Unit) = TextView(ctx).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(ctx, 20), dp(ctx, 14), dp(ctx, 20), dp(ctx, 14))
        isFocusable = true
        isClickable = true
        val focused = glass(ctx, withAlpha(accent, 0xDD), accent, 14)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(android.R.attr.state_pressed), focused)
            addState(intArrayOf(), glass(ctx, 0x77000000, 0x33FFFFFF, 14))
        }
        setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                intArrayOf(Color.BLACK, Color.BLACK, Color.WHITE),
            ),
        )
        setOnClickListener { onClick() }
    }

    class ListAdapter(private val ctx: Context, items: List<String>) : ArrayAdapter<String>(ctx, 0, items) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val t = (convertView as? TextView) ?: TextView(ctx).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTextColor(Color.WHITE)
                setPadding(dp(ctx, 20), dp(ctx, 12), dp(ctx, 20), dp(ctx, 12))
            }
            t.text = getItem(position)
            return t
        }
    }
}
