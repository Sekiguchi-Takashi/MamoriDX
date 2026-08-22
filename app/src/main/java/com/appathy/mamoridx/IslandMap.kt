package com.appathy.mamoridx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

/**
 * 島の俯瞰マップを描画する。
 * 画像素材ではなくCanvasで描くので、端末の画面幅に合わせて常に綺麗に出る。
 * 9エリアの座標は「海沿いは外周／高所は中央」という地形の理屈に沿って配置している。
 */
object IslandMap {

    /** 位置は GameData.Area の mapX / mapY を唯一の情報源とする */
    private fun pos(id: String): Pair<Float, Float>? =
        GameData.areas.firstOrNull { it.id == id }?.let { it.mapX to it.mapY }

    /** 道でつなぐ組み合わせ */
    private val paths = listOf(
        "beach" to "pier", "beach" to "forest", "forest" to "cave",
        "forest" to "temple", "temple" to "volcano", "temple" to "ruins",
        "volcano" to "waterfall", "waterfall" to "lighthouse",
        "ruins" to "pier", "ruins" to "lighthouse"
    )

    private val seaTop = Color.parseColor("#7FD9E8")
    private val seaBottom = Color.parseColor("#1E9BC4")
    private val sandColor = Color.parseColor("#F0D9A4")
    private val landColor = Color.parseColor("#6FBF63")
    private val landDark = Color.parseColor("#4E9E48")
    private val rockColor = Color.parseColor("#8D7F6E")
    private val nightSea = Color.parseColor("#1B3B63")
    private val nightLand = Color.parseColor("#2F5B3A")
    private val nightSand = Color.parseColor("#8E7F5E")

    fun draw(ctx: Context, w: Int, h: Int, night: Boolean): Drawable {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        fun fx(v: Float) = v * w
        fun fy(v: Float) = v * h

        // ---- 海 ----
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            if (night) Color.parseColor("#20527F") else seaTop,
            if (night) nightSea else seaBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        // 波（等間隔の弧）
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.004f
        p.color = Color.argb(if (night) 40 else 70, 255, 255, 255)
        var row = 0
        var y = h * 0.06f
        while (y < h * 0.97f) {
            var x = if (row % 2 == 0) w * 0.03f else w * 0.10f
            while (x < w * 0.97f) {
                val r = RectF(x, y, x + w * 0.05f, y + h * 0.02f)
                c.drawArc(r, 200f, 140f, false, p)
                x += w * 0.14f
            }
            y += h * 0.075f
            row++
        }
        p.style = Paint.Style.FILL

        // ---- 砂浜 ----
        p.color = if (night) nightSand else sandColor
        c.drawOval(RectF(fx(0.06f), fy(0.10f), fx(0.95f), fy(0.87f)), p)

        // ---- 陸 ----
        p.color = if (night) nightLand else landColor
        c.drawOval(RectF(fx(0.10f), fy(0.13f), fx(0.91f), fy(0.82f)), p)

        // 森の陰影
        p.color = if (night) Color.parseColor("#26492F") else landDark
        c.drawOval(RectF(fx(0.14f), fy(0.28f), fx(0.48f), fy(0.62f)), p)
        c.drawOval(RectF(fx(0.55f), fy(0.30f), fx(0.86f), fy(0.60f)), p)

        // ---- 火山 ----
        val vx = fx(0.50f); val vy = fy(0.20f)
        val vw = w * 0.20f; val vh = h * 0.16f
        val cone = Path().apply {
            moveTo(vx - vw / 2, vy + vh / 2)
            lineTo(vx - vw * 0.16f, vy - vh / 2)
            lineTo(vx + vw * 0.16f, vy - vh / 2)
            lineTo(vx + vw / 2, vy + vh / 2)
            close()
        }
        p.color = if (night) Color.parseColor("#4A4038") else rockColor
        c.drawPath(cone, p)
        p.color = Color.parseColor(if (night) "#FF8A3D" else "#FF7043")
        c.drawOval(RectF(vx - vw * 0.17f, vy - vh * 0.60f,
            vx + vw * 0.17f, vy - vh * 0.40f), p)

        // ---- 灯台の岬 ----
        p.color = if (night) Color.parseColor("#4A4038") else rockColor
        c.drawOval(RectF(fx(0.78f), fy(0.24f), fx(0.93f), fy(0.37f)), p)

        // ---- 滝 ----
        p.color = Color.argb(if (night) 150 else 220, 255, 255, 255)
        c.drawRect(fx(0.645f), fy(0.30f), fx(0.675f), fy(0.42f), p)

        // ---- 道（点線） ----
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.008f
        p.color = Color.argb(if (night) 90 else 150, 255, 250, 235)
        p.pathEffect = DashPathEffect(floatArrayOf(w * 0.018f, w * 0.014f), 0f)
        for ((a, b) in paths) {
            val pa = pos(a) ?: continue
            val pb = pos(b) ?: continue
            c.drawLine(fx(pa.first), fy(pa.second), fx(pb.first), fy(pb.second), p)
        }
        p.pathEffect = null
        p.style = Paint.Style.FILL

        // ---- 椰子の点（雰囲気づけ・固定配置） ----
        p.color = if (night) Color.parseColor("#1F3D28") else Color.parseColor("#3E8C3A")
        val seed = 7
        for (i in 0 until 26) {
            val ang = (i * 137 + seed) % 360 * Math.PI / 180.0
            val rad = 0.12f + (i % 5) * 0.055f
            val px = 0.50f + (rad * cos(ang)).toFloat() * 0.95f
            val py = 0.48f + (rad * sin(ang)).toFloat() * 0.80f
            if (px < 0.14f || px > 0.88f || py < 0.16f || py > 0.80f) continue
            c.drawCircle(fx(px), fy(py), w * 0.012f, p)
        }

        return BitmapDrawable(ctx.resources, bmp)
    }
}
