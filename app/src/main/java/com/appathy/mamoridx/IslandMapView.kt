package com.appathy.mamoridx

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * 島の地図。
 *
 * 背景画像とピンを **同じ座標系で自分で描く** ため、
 * レイアウトの解釈違いで両者がズレることが原理的に起こらない。
 * タップ判定も同じ座標で行う。
 */
class IslandMapView(
    ctx: Context,
    private val mapW: Int,
    private val mapH: Int,
    private val background: Drawable,
    private val pins: List<Pin>,
    private val onPick: (String) -> Unit
) : View(ctx) {

    data class Pin(
        val areaId: String,
        val label: String,
        val x: Float,          // 0.0〜1.0
        val y: Float,          // 0.0〜1.0
        val icon: Drawable,
        val locked: Boolean,
        val marked: Boolean    // まだ何かありそう
    )

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val iconSize = dp(34f)
    private val hitRadius = dp(30f)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        color = Color.WHITE
    }
    private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 0, 0)
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2C14E")
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(mapW, mapH)
    }

    override fun onDraw(canvas: Canvas) {
        // ---- 背景（必ず 0,0 〜 mapW,mapH に敷き詰める） ----
        background.setBounds(0, 0, mapW, mapH)
        background.draw(canvas)

        // ---- ピン ----
        for (p in pins) {
            val cx = p.x * mapW
            val cy = p.y * mapH

            val alpha = if (p.locked) 110 else 255
            // アイコンの「先端」が座標に来るように、底辺を cy に合わせる
            val left = (cx - iconSize / 2f).toInt()
            val top = (cy - iconSize).toInt()
            p.icon.setBounds(left, top, (left + iconSize).toInt(), cy.toInt())
            p.icon.alpha = alpha
            p.icon.draw(canvas)

            // ラベル（座標のすぐ下）
            val label = p.label
            val tw = textPaint.measureText(label)
            val ty = cy + dp(16f)
            canvas.drawRoundRect(
                RectF(cx - tw / 2f - dp(6f), ty - dp(12f),
                    cx + tw / 2f + dp(6f), ty + dp(4f)),
                dp(4f), dp(4f), textBgPaint)
            textPaint.alpha = alpha
            canvas.drawText(label, cx, ty, textPaint)

            // 未回収の欠片がありそうな場所に印
            if (p.marked && !p.locked) {
                canvas.drawCircle(cx + tw / 2f + dp(11f), ty - dp(4f), dp(4f), markPaint)
            }
        }
    }

    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                // 横スクロールとの誤爆を避けるため、あまり動いていない時だけ拾う
                if (abs(event.x - downX) > dp(12f) || abs(event.y - downY) > dp(12f)) {
                    return true
                }
                val hit = pins
                    .filter { !it.locked }
                    .minByOrNull { p ->
                        val dx = event.x - p.x * mapW
                        val dy = event.y - (p.y * mapH - iconSize / 2f)
                        dx * dx + dy * dy
                    } ?: return true
                val dx = event.x - hit.x * mapW
                val dy = event.y - (hit.y * mapH - iconSize / 2f)
                if (dx * dx + dy * dy <= hitRadius * hitRadius * 2.2f) {
                    onPick(hit.areaId)
                }
                return true
            }
        }
        return true
    }
}
