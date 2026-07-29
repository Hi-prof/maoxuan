package com.xuhuangbin.xinghuozhaidu.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xuhuangbin.xinghuozhaidu.domain.model.CardInterpretation
import com.xuhuangbin.xinghuozhaidu.domain.model.CardSource
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareCardRendererInstrumentedTest {
    @Test
    fun rendersFixedNonBlankImageWithBackground() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val background = File(context.cacheDir, "share-background-${UUID.randomUUID()}.png")
        createBackground(background)
        val card = QuoteCard(
            id = "b85d8407-3b74-4c5e-b516-b032a22d73aa",
            revision = 1,
            quote = "我们的结论是主观和客观、理论和实践、知和行的具体的历史的统一，反对一切离开具体历史的“左”的或右的错误思想。",
            series = "毛泽东选集",
            volume = "第一卷",
            workTitle = "实践论",
            authoredAt = "1937-07",
            themes = listOf("实践", "认识"),
            interpretation = CardInterpretation(
                inspiration = "用行动结果持续校正判断。",
                explanation = "认识应在实践中形成并接受检验，理论与实践需要具体地、历史地统一。",
            ),
            historicalEvent = "1937年7月，毛泽东在延安讲授哲学问题。",
            background = "文章讨论认识与实践的关系。",
            story = "讲授内容后来整理为《实践论》。",
            imagePath = background.absolutePath,
            sources = listOf(
                CardSource("原文", "https://example.com/a", "2026-07-28", "original"),
                CardSource("校核", "https://example.org/b", "2026-07-28", "authoritative"),
            ),
            isWithdrawn = false,
            isLiked = false,
            isFavorited = false,
            likedAt = null,
            favoritedAt = null,
        )

        val output = ShareCardRenderer.render(context, card)
        val rendered = BitmapFactory.decodeFile(output.absolutePath)

        assertEquals(1080, rendered.width)
        assertEquals(1440, rendered.height)
        assertNotEquals(Color.rgb(247, 245, 239), rendered.getPixel(900, 900))
        var quotePixelFound = false
        for (x in 90 until 990 step 8) {
            for (y in 260 until 1_000 step 8) {
                val pixel = rendered.getPixel(x, y)
                if (Color.red(pixel) < 100 && Color.green(pixel) < 100 && Color.blue(pixel) < 100) {
                    quotePixelFound = true
                    break
                }
            }
            if (quotePixelFound) break
        }
        assertTrue(quotePixelFound)

        rendered.recycle()
        background.delete()
        output.delete()
    }

    private fun createBackground(output: File) {
        val bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(26, 118, 164))
        }
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        bitmap.recycle()
    }
}
