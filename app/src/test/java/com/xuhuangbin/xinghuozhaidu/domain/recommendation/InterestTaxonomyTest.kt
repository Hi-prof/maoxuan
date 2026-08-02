package com.xuhuangbin.xinghuozhaidu.domain.recommendation

import com.xuhuangbin.xinghuozhaidu.data.content.ContentPackageReader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterestTaxonomyTest {
    @Test
    fun exposesTwelveStableInterests() {
        assertEquals(
            listOf(
                "self_growth" to "自我成长",
                "learning" to "学习求知",
                "life_wisdom" to "人生智慧",
                "ideals" to "理想奋斗",
                "courage" to "勇气行动",
                "practice" to "实践求真",
                "philosophy" to "哲学思辨",
                "labor" to "劳动创造",
                "relationships" to "人际关系",
                "people_society" to "人民社会",
                "history" to "历史时代",
                "poetry" to "诗词文学",
            ),
            InterestCategory.entries.map { it.id to it.label },
        )
    }

    @Test
    fun mapsThemesAndSeriesToSeveralRelevantInterests() {
        val practice = testCard("practice", series = "毛泽东选集", themes = listOf("调查研究", "群众路线"))
        assertEquals(
            setOf(InterestCategory.Practice, InterestCategory.PeopleSociety),
            InterestTaxonomy.categoriesFor(practice),
        )

        val poem = testCard("poem", series = "毛泽东诗词", themes = listOf("奋斗"))
        assertTrue(InterestCategory.Poetry in InterestTaxonomy.categoriesFor(poem))
        assertTrue(InterestCategory.Ideals in InterestTaxonomy.categoriesFor(poem))
    }

    @Test
    fun unknownThemesRemainUnclassifiedForExploration() {
        val card = testCard("unknown", series = "未知系列", themes = listOf("未知主题"))
        assertTrue(InterestTaxonomy.categoriesFor(card).isEmpty())
    }

    @Test
    fun bundledContentProvidesAMeaningfulPoolForEveryInterest() {
        val cards = ContentPackageReader()
            .read(File("src/main/assets/bootstrap.zip").readBytes())
            .cards
            .map { card -> testCard(card.id, card.series, card.themes) }

        assertEquals(600, cards.size)
        assertTrue(cards.all { InterestTaxonomy.categoriesFor(it).isNotEmpty() })
        InterestCategory.entries.forEach { category ->
            val candidateCount = cards.count { category in InterestTaxonomy.categoriesFor(it) }
            assertTrue("${category.id} only maps to $candidateCount bundled cards", candidateCount >= 15)
        }
    }
}
