package com.taki.tiktokcompanion.banner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View

data class BannerData(
    val title: String,
    val subtitle: String,
    val footer: String,
    val theme: Int = 0
)

class BannerPreviewView(context: Context, private var bannerData: BannerData) : View(context) {
    fun update(data: BannerData) {
        bannerData = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        BannerRenderer.draw(canvas, width.toFloat(), height.toFloat(), bannerData)
    }
}

object BannerRenderer {
    private data class Palette(val start: Int, val end: Int, val accent: Int)

    private val palettes = listOf(
        Palette(0xFF171936.toInt(), 0xFF6A4C93.toInt(), 0xFFFFC6FF.toInt()),
        Palette(0xFF141E30.toInt(), 0xFF3A6073.toInt(), 0xFF9FE7F5.toInt()),
        Palette(0xFF42275A.toInt(), 0xFF734B6D.toInt(), 0xFFFFD6A5.toInt()),
        Palette(0xFF090A0F.toInt(), 0xFF252B48.toInt(), 0xFF9B9FFF.toInt())
    )

    fun createBitmap(data: BannerData, width: Int = 1440, height: Int = 560): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), width.toFloat(), height.toFloat(), data)
        return bitmap
    }

    fun draw(canvas: Canvas, width: Float, height: Float, data: BannerData) {
        if (width <= 0f || height <= 0f) return
        val palette = palettes[data.theme.mod(palettes.size)]
        val sx = width / 1440f
        val sy = height / 560f
        val scale = minOf(sx, sy)

        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width, height, palette.start, palette.end, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width, height, background)
        background.shader = null

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent; alpha = 28 }
        canvas.drawCircle(width * 0.84f, height * 0.08f, height * 0.72f, glow)
        glow.alpha = 18
        canvas.drawCircle(width * 0.08f, height * 0.95f, height * 0.62f, glow)

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * scale
            color = 0x66FFFFFF
        }
        val inset = 30f * scale
        canvas.drawRoundRect(
            RectF(inset, inset, width - inset, height - inset),
            34f * scale, 34f * scale, outline
        )

        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent }
        canvas.drawRoundRect(
            RectF(width * 0.09f, height * 0.22f, width * 0.095f, height * 0.78f),
            8f * scale, 8f * scale, accent
        )

        val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xD9FFFFFF.toInt()
            textSize = 26f * scale
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 76f * scale
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(8f * scale, 0f, 3f * scale, 0x55000000)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xF0FFFFFF.toInt()
            textSize = 34f * scale
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            textSize = 24f * scale
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        val eyebrow = "TIKTOK COMPANION  •  YOUR SPACE"
        canvas.drawText(eyebrow, width / 2f, height * 0.28f, eyebrowPaint)
        val title = data.title.ifBlank { "YOUR BANNER" }.take(42)
        val titleLines = wrap(title, titlePaint, width * 0.78f).take(2)
        val titleStart = if (titleLines.size == 1) height * 0.49f else height * 0.44f
        titleLines.forEachIndexed { index, line ->
            canvas.drawText(line, width / 2f, titleStart + index * 82f * scale, titlePaint)
        }

        val subtitle = data.subtitle.ifBlank { "A little space that feels like you." }.take(110)
        val subtitleLines = wrap(subtitle, subtitlePaint, width * 0.78f).take(2)
        val subtitleStart = if (titleLines.size == 1) height * 0.66f else height * 0.72f
        subtitleLines.forEachIndexed { index, line ->
            canvas.drawText(line, width / 2f, subtitleStart + index * 42f * scale, subtitlePaint)
        }
        val footer = data.footer.trim().take(48)
        if (footer.isNotBlank()) canvas.drawText(footer, width / 2f, height * 0.88f, footerPaint)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAAFFFFFF.toInt() }
        val points = listOf(
            width * 0.17f to height * 0.18f,
            width * 0.82f to height * 0.78f,
            width * 0.88f to height * 0.24f,
            width * 0.21f to height * 0.82f
        )
        points.forEachIndexed { index, point ->
            canvas.drawCircle(point.first, point.second, (3 + index % 2) * scale, dotPaint)
        }
    }

    private fun wrap(value: String, paint: Paint, maxWidth: Float): List<String> {
        val words = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var line = ""
        words.forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                lines += line
                line = word
            } else line = candidate
            if (line.isNotEmpty() && paint.measureText(line) > maxWidth) {
                var chunk = ""
                line.forEach { character ->
                    val next = chunk + character
                    if (chunk.isNotEmpty() && paint.measureText(next) > maxWidth) {
                        lines += chunk
                        chunk = character.toString()
                    } else chunk = next
                }
                line = chunk
            }
        }
        if (line.isNotBlank()) lines += line
        return lines
    }
}
