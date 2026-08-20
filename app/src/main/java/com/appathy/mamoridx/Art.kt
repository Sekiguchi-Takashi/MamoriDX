package com.appathy.mamoridx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView

/**
 * 画像の読み込み。
 * res/drawable に該当画像があればそれを使い、無ければ色板を自動生成して代替する。
 * これにより、画像が1枚も無い状態でもアプリは完全に動作する。
 */
object Art {

    private val cache = HashMap<String, Drawable>()

    /** drawable名から画像を取得。無ければプレースホルダを返す */
    fun get(ctx: Context, name: String, label: String, w: Int = 720, h: Int = 405): Drawable {
        cache[name]?.let { return it }
        val id = try {
            ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        } catch (e: Exception) { 0 }
        val d: Drawable = if (id != 0) {
            try {
                ctx.resources.getDrawable(id, ctx.theme)
            } catch (e: Exception) {
                placeholder(ctx, name, label, w, h)
            }
        } else {
            placeholder(ctx, name, label, w, h)
        }
        cache[name] = d
        return d
    }

    fun into(iv: ImageView, ctx: Context, name: String, label: String,
             w: Int = 720, h: Int = 405) {
        iv.setImageDrawable(get(ctx, name, label, w, h))
    }

    fun hasImage(ctx: Context, name: String): Boolean = try {
        ctx.resources.getIdentifier(name, "drawable", ctx.packageName) != 0
    } catch (e: Exception) { false }

    /** 画像未配置のときに表示する色板。名前から色を決めるので毎回同じ見た目になる */
    private fun placeholder(ctx: Context, name: String, label: String,
                            w: Int, h: Int): Drawable {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val hue = (Math.abs(name.hashCode()) % 360).toFloat()
        val top = Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.85f))
        val bottom = Color.HSVToColor(floatArrayOf((hue + 25f) % 360f, 0.55f, 0.55f))

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        // ラベル
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.color = Color.WHITE
        tp.textSize = (h * 0.13f)
        tp.textAlign = Paint.Align.CENTER
        tp.isFakeBoldText = true
        c.drawText(label, w / 2f, h / 2f + tp.textSize / 3f, tp)

        val sp = Paint(Paint.ANTI_ALIAS_FLAG)
        sp.color = Color.argb(150, 255, 255, 255)
        sp.textSize = (h * 0.07f)
        sp.textAlign = Paint.Align.CENTER
        c.drawText("画像未配置: $name", w / 2f, h - sp.textSize, sp)

        return BitmapDrawable(ctx.resources, bmp)
    }

    fun clearCache() = cache.clear()
}
