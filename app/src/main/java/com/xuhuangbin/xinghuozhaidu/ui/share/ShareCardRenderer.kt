package com.xuhuangbin.xinghuozhaidu.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.xuhuangbin.xinghuozhaidu.R
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShareCardRenderer {
    private const val WIDTH = 1080
    private const val HEIGHT = 1440

    suspend fun share(context: Context, card: QuoteCard) {
        val file = withContext(Dispatchers.IO) { render(context, card) }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享名言卡片"))
    }

    fun render(context: Context, card: QuoteCard): File {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(247, 245, 239))
        drawBackground(canvas, card.imagePath)

        val red = Color.rgb(165, 42, 46)
        val ink = Color.rgb(39, 37, 34)
        val muted = Color.rgb(99, 94, 87)
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = red
            strokeWidth = 8f
        }
        canvas.drawLine(110f, 176f, 250f, 176f, accent)

        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = quoteTypeface(context)
            val quoteLength = card.quote.codePointCount(0, card.quote.length)
            textSize = when {
                quoteLength <= 32 -> 78f
                quoteLength <= 60 -> 68f
                else -> 60f
            }
        }
        var quoteLayout = staticLayout(card.quote, quotePaint, 860)
        while (quoteLayout.height > 720 && quotePaint.textSize > 50f) {
            quotePaint.textSize -= 2f
            quoteLayout = staticLayout(card.quote, quotePaint, 860)
        }
        canvas.save()
        canvas.translate(110f, 300f)
        quoteLayout.draw(canvas)
        canvas.restore()

        val sourcePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = red
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 34f
        }
        val source = "《${card.workTitle}》  ${card.series} ${card.volume}"
        canvas.save()
        canvas.translate(110f, 1080f)
        staticLayout(source, sourcePaint, 820).draw(canvas)
        canvas.restore()

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 27f
        }
        canvas.drawText(card.authoredAt, 110f, 1195f, metaPaint)
        val brandPaint = Paint(metaPaint).apply {
            color = red
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 29f
        }
        canvas.drawText("星火摘读", 110f, 1325f, brandPaint)

        val directory = File(context.cacheDir, "shares").apply { mkdirs() }
        cleanShareCache(directory)
        val output = File(directory, "quote-${card.id}.png")
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        bitmap.recycle()
        return output
    }

    private fun cleanShareCache(directory: File) {
        val files = directory.listFiles()?.filter(File::isFile).orEmpty()
        val expiredBefore = System.currentTimeMillis() - 86_400_000L
        files.filter { it.lastModified() < expiredBefore }.forEach(File::delete)
        directory.listFiles()
            ?.filter(File::isFile)
            ?.sortedByDescending(File::lastModified)
            ?.drop(20)
            ?.forEach(File::delete)
    }

    private fun quoteTypeface(context: Context): Typeface {
        val bundled = checkNotNull(ResourcesCompat.getFont(context, R.font.noto_serif_sc)) {
            "Bundled quote font could not be loaded"
        }
        return Typeface.create(bundled, Typeface.BOLD)
    }

    private fun drawBackground(canvas: Canvas, path: String) {
        val source = BitmapFactory.decodeFile(path) ?: return
        val sourceRatio = source.width.toFloat() / source.height
        val targetRatio = WIDTH.toFloat() / HEIGHT
        val src = if (sourceRatio > targetRatio) {
            val targetWidth = (source.height * targetRatio).toInt()
            val left = (source.width - targetWidth) / 2
            Rect(left, 0, left + targetWidth, source.height)
        } else {
            val targetHeight = (source.width / targetRatio).toInt()
            val top = (source.height - targetHeight) / 2
            Rect(0, top, source.width, top + targetHeight)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 43 }
        canvas.drawBitmap(source, src, Rect(0, 0, WIDTH, HEIGHT), paint)
        source.recycle()
    }

    private fun staticLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(12f, 1.18f)
            .build()
}
