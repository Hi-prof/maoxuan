package com.xuhuangbin.xinghuozhaidu.data

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xuhuangbin.xinghuozhaidu.data.local.XinghuoDatabase
import com.xuhuangbin.xinghuozhaidu.domain.recommendation.InterestCategory
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppRepositoryInstrumentedTest {
    private lateinit var database: XinghuoDatabase
    private lateinit var repository: AppRepository
    private lateinit var testRoot: File
    private lateinit var isolatedContext: Context

    private val cardId = "d99d5cbc-1193-5d91-8896-7969c9393c53"
    private val imageId = "integration-paper"
    private val imageBytes by lazy(::createPng)
    private var currentTime = 1_800_000_000_000L

    @Before
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        testRoot = File(baseContext.cacheDir, "repository-tests/${UUID.randomUUID()}").apply {
            check(mkdirs())
        }
        isolatedContext = object : ContextWrapper(baseContext) {
            override fun getFilesDir(): File = testRoot
        }
        database = Room.inMemoryDatabaseBuilder(
            baseContext,
            XinghuoDatabase::class.java,
        ).build()
        repository = AppRepository(
            context = isolatedContext,
            database = database,
            random = Random(7),
            now = { currentTime },
        )
    }

    @After
    fun tearDown() {
        database.close()
        testRoot.deleteRecursively()
    }

    @Test
    fun revisionWithdrawalRestoreAndFinalStateRemovalStayConsistent() = runBlocking {
        repository.importPackage(buildPackage("1.0.0", 1, "实践是检验真理的标准。"))
        val attribution = requireNotNull(repository.allCards.first().single().imageAttribution)
        assertEquals("Integration fixture", attribution.creator)
        assertEquals("https://example.com/image", attribution.sourceUrl)
        assertEquals("CC0-1.0", attribution.licenseName)
        assertEquals(
            "https://creativecommons.org/publicdomain/zero/1.0/",
            attribution.licenseEvidence,
        )
        repository.toggleLike(cardId)
        repository.toggleFavorite(cardId)

        repository.importPackage(buildPackage("1.1.0", 2, "修订后的连续原文引文。"))
        val revised = repository.allCards.first().single()
        assertEquals(2, revised.revision)
        assertEquals("认识必须回到实践中检验。", revised.interpretation.explanation)
        assertTrue(revised.isLiked)
        assertTrue(revised.isFavorited)

        repository.importPackage(buildPackage("1.2.0", withdrawalRevision = 3))
        val withdrawn = repository.allCards.first().single()
        assertTrue(withdrawn.isWithdrawn)
        assertTrue(withdrawn.isLiked)
        assertTrue(withdrawn.isFavorited)
        assertTrue(repository.activeCards.first().isEmpty())

        repository.importPackage(buildPackage("1.3.0", 4, "恢复发布后的最新内容。"))
        val restored = repository.activeCards.first().single()
        assertFalse(restored.isWithdrawn)
        assertEquals(4, restored.revision)
        assertTrue(restored.isLiked)
        assertTrue(restored.isFavorited)
        assertEquals(cardId, repository.readerState.first().cards.single().id)

        repository.importPackage(buildPackage("1.4.0", withdrawalRevision = 5))
        repository.toggleFavorite(cardId)
        val likedSnapshot = repository.allCards.first().single()
        assertTrue(likedSnapshot.isWithdrawn)
        assertTrue(likedSnapshot.isLiked)
        assertFalse(likedSnapshot.isFavorited)
        val assetPath = database.appDao().getImages().single().localPath
        assertTrue(File(assetPath).isFile)

        repository.toggleLike(cardId)
        assertTrue(repository.allCards.first().isEmpty())
        assertNull(database.appDao().getCard(cardId))
        assertNull(database.appDao().getUserState(cardId))
        assertTrue(database.appDao().getImages().isEmpty())
        assertFalse(File(assetPath).exists())
    }

    @Test
    fun rejectsImageMetadataThatDoesNotMatchDecodedAsset() = runBlocking {
        try {
            repository.importPackage(
                buildPackage(
                    contentVersion = "1.0.0",
                    revision = 1,
                    quote = "实践是检验真理的标准。",
                    declaredWidth = 721,
                ),
            )
            fail("应拒绝与声明尺寸不一致的图片")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("实际尺寸"))
        }

        assertNull(database.appDao().getContentState())
        assertTrue(database.appDao().getImages().isEmpty())
        assertTrue(File(testRoot, "content/assets").listFiles().isNullOrEmpty())
    }

    @Test
    fun searchHistoryKeepsTenRecentUniqueQueriesAndSupportsRemoval() = runBlocking {
        repository.saveSearchQuery("   ")
        assertTrue(repository.searchHistory.first().isEmpty())

        (0..10).forEach { index -> repository.saveSearchQuery("关键词$index") }
        assertEquals(
            (10 downTo 1).map { index -> "关键词$index" },
            repository.searchHistory.first(),
        )

        repository.saveSearchQuery("  关键词5  ")
        assertEquals("关键词5", repository.searchHistory.first().first())
        assertEquals(10, repository.searchHistory.first().size)

        repository.saveSearchQuery("Theory")
        repository.saveSearchQuery("theory")
        val deduplicated = repository.searchHistory.first()
        assertEquals("theory", deduplicated.first())
        assertEquals(1, deduplicated.count { it.equals("theory", ignoreCase = true) })
        assertEquals(10, deduplicated.size)

        repository.deleteSearchHistory("THEORY")
        assertFalse(repository.searchHistory.first().any { it.equals("theory", ignoreCase = true) })

        repository.clearSearchHistory()
        assertTrue(repository.searchHistory.first().isEmpty())
    }

    @Test
    fun initializeInstallsNewerBundledContentWithoutLosingPersonalState() = runBlocking {
        repository.importPackage(buildPackage("1.0.0", 1, "谁是我们的敌人？谁是我们的朋友？"))
        repository.toggleLike(cardId)
        repository.toggleFavorite(cardId)
        repository.markRead(cardId)

        repository.initialize()

        val installed = database.appDao().getContentState()
        assertEquals("1.6.0", installed?.contentVersion)
        val upgraded = repository.allCards.first().first { it.id == cardId }
        assertEquals(4, upgraded.revision)
        assertTrue(upgraded.isLiked)
        assertTrue(upgraded.isFavorited)
        val round = database.appDao().getActiveRound()
        val upgradedItem = database.appDao().getRoundItems(requireNotNull(round).id)
            .first { it.cardId == cardId }
        assertTrue(upgradedItem.readAt != null)
    }

    @Test
    fun freshInitializationWaitsForInterestsAndPersistsRecommendationSettings() = runBlocking {
        repository.initialize()

        assertTrue(repository.recommendationSettings.first().requiresOnboarding)
        assertNull(repository.readerState.first().roundId)

        repository.completeInterestOnboarding(
            setOf(InterestCategory.SelfGrowth.id, InterestCategory.Practice.id),
        )

        val settings = repository.recommendationSettings.first()
        assertFalse(settings.requiresOnboarding)
        assertEquals(
            setOf(InterestCategory.SelfGrowth, InterestCategory.Practice),
            settings.selected,
        )
        val reader = repository.readerState.first()
        assertTrue(reader.cards.isNotEmpty())

        val reducedCardId = reader.cards.first().id
        repository.reduceSimilarContent(reducedCardId)
        assertEquals(1, repository.recommendationSettings.first().reducedCount)
        val round = requireNotNull(database.appDao().getActiveRound())
        assertTrue(
            database.appDao().getRoundItems(round.id)
                .first { it.cardId == reducedCardId }
                .readAt != null,
        )

        repository.clearReducedContentFeedback()
        assertEquals(0, repository.recommendationSettings.first().reducedCount)
    }

    @Test
    fun interestPreferencesValidateReplaceAndPersistAcrossRepositoryInstances() = runBlocking {
        repository.initialize()

        try {
            repository.completeInterestOnboarding(
                InterestCategory.entries.take(6).mapTo(mutableSetOf(), InterestCategory::id),
            )
            fail("应拒绝超过 5 个兴趣标签")
        } catch (error: IllegalArgumentException) {
            assertEquals("最多选择 5 个兴趣标签", error.message)
        }
        try {
            repository.completeInterestOnboarding(setOf("unknown"))
            fail("应拒绝未知兴趣标签")
        } catch (error: IllegalArgumentException) {
            assertEquals("兴趣标签无效", error.message)
        }

        repository.completeInterestOnboarding(
            setOf(InterestCategory.SelfGrowth.id, InterestCategory.Practice.id),
        )
        repository.saveRecommendationPreferences(setOf(InterestCategory.Learning.id), emptySet())

        assertEquals(
            setOf(InterestCategory.Learning),
            repository.recommendationSettings.first().selected,
        )
        val restarted = AppRepository(
            context = isolatedContext,
            database = database,
            random = Random(9),
            now = { currentTime },
        )
        assertEquals(
            setOf(InterestCategory.Learning),
            restarted.recommendationSettings.first().selected,
        )
        assertFalse(restarted.recommendationSettings.first().requiresOnboarding)
    }

    @Test
    fun contentSeriesPreferencesFilterOnlyTheReaderAndPersistAcrossRepositories() = runBlocking {
        repository.initialize()
        repository.completeInterestOnboarding(emptySet())
        val allCards = repository.activeCards.first()
        val availableSeries = allCards.mapTo(mutableSetOf()) { it.series }
        assertEquals(
            setOf("毛泽东选集", "毛泽东诗词", "名人名言", "马原思考"),
            availableSeries,
        )
        assertTrue(repository.recommendationSettings.first().selectedSeries.isEmpty())
        assertEquals(availableSeries, repository.readerState.first().cards.mapTo(mutableSetOf()) { it.series })
        try {
            repository.saveRecommendationPreferences(emptySet(), setOf(" "))
            fail("应拒绝空白内容系列")
        } catch (error: IllegalArgumentException) {
            assertEquals("内容范围无效", error.message)
        }
        assertTrue(repository.recommendationSettings.first().selectedSeries.isEmpty())

        repository.saveRecommendationPreferences(
            interestIds = setOf(InterestCategory.Practice.id),
            selectedSeries = setOf("毛泽东选集"),
        )

        val singleSeriesReader = repository.readerState.first()
        assertTrue(singleSeriesReader.cards.isNotEmpty())
        assertTrue(singleSeriesReader.cards.all { it.series == "毛泽东选集" })
        val outsideCard = allCards.first { it.series == "名人名言" }
        repository.toggleFavorite(outsideCard.id)
        assertTrue(repository.favorites.first().any { it.id == outsideCard.id })
        assertTrue(repository.search(outsideCard.workTitle).first().any { it.id == outsideCard.id })
        assertTrue(repository.readerState.first().cards.all { it.series == "毛泽东选集" })

        val multiSelection = setOf("毛泽东选集", "毛泽东诗词")
        repository.saveRecommendationPreferences(emptySet(), multiSelection)
        repository.startNewRound()
        assertEquals(
            multiSelection,
            repository.readerState.first().cards.mapTo(mutableSetOf()) { it.series },
        )

        val restarted = AppRepository(
            context = isolatedContext,
            database = database,
            random = Random(11),
            now = { currentTime },
        )
        assertEquals(multiSelection, restarted.recommendationSettings.first().selectedSeries)
        assertEquals(
            multiSelection,
            restarted.readerState.first().cards.mapTo(mutableSetOf()) { it.series },
        )
    }

    @Test
    fun notesSupportStandaloneAndMultipleCardEntriesWhileRetainingWithdrawnSnapshot() = runBlocking {
        repository.importPackage(buildPackage("1.0.0", 1, "实践是检验真理的标准。"))

        val standaloneId = repository.saveNote(null, null, "  随想  ", "  独立笔记正文  ")
        currentTime += 1
        val firstCardNoteId = repository.saveNote(null, cardId, "第一次阅读", "第一篇卡片笔记")
        currentTime += 1
        val secondCardNoteId = repository.saveNote(null, cardId, "", "第二篇卡片笔记")

        val created = repository.notes.first()
        assertEquals(listOf(secondCardNoteId, firstCardNoteId, standaloneId), created.map { it.id })
        assertNull(created.first().title)
        assertEquals("随想", created.last().title)
        assertEquals("独立笔记正文", created.last().body)

        currentTime += 1
        repository.saveNote(firstCardNoteId, cardId, "修改后", "修改后的正文")
        val updated = repository.notes.first().first()
        assertEquals(firstCardNoteId, updated.id)
        assertEquals("修改后", updated.title)
        assertEquals("修改后的正文", updated.body)

        repository.importPackage(buildPackage("1.1.0", withdrawalRevision = 2))
        assertTrue(repository.allCards.first().single().isWithdrawn)

        repository.deleteNote(firstCardNoteId)
        assertTrue(repository.allCards.first().single().isWithdrawn)
        repository.deleteNote(secondCardNoteId)
        assertTrue(repository.allCards.first().isEmpty())
        assertEquals(listOf(standaloneId), repository.notes.first().map { it.id })
    }

    @Test
    fun noteValidationRejectsBlankBodyAndAssociationChanges() = runBlocking {
        try {
            repository.saveNote(null, null, "标题", " \n\t ")
            fail("应拒绝空白笔记正文")
        } catch (error: IllegalArgumentException) {
            assertEquals("笔记正文不能为空", error.message)
        }
        assertTrue(repository.notes.first().isEmpty())

        repository.importPackage(buildPackage("1.0.0", 1, "实践是检验真理的标准。"))
        val noteId = repository.saveNote(null, cardId, "关联笔记", "正文")
        try {
            repository.saveNote(noteId, null, "改为独立笔记", "修改正文")
            fail("应拒绝修改既有笔记的卡片关联")
        } catch (error: IllegalArgumentException) {
            assertEquals("不能修改笔记关联的卡片", error.message)
        }

        val unchanged = repository.notes.first().single()
        assertEquals(cardId, unchanged.cardId)
        assertEquals("关联笔记", unchanged.title)
        assertEquals("正文", unchanged.body)
    }

    private fun buildPackage(
        contentVersion: String,
        revision: Int? = null,
        quote: String? = null,
        withdrawalRevision: Int? = null,
        declaredWidth: Int = 720,
    ): ByteArray {
        val imageHash = imageBytes.sha256()
        val assetName = "assets/$imageHash.png"
        val cardsJson = if (revision == null) {
            """{"schemaVersion":4,"cards":[]}"""
        } else {
            """
                {"schemaVersion":4,"cards":[{
                  "id":"$cardId",
                  "revision":$revision,
                  "status":"published",
                  "quote":"$quote",
                  "series":"毛泽东选集",
                  "volume":"第一卷",
                  "workTitle":"实践论",
                  "authoredAt":"1937-07",
                  "themes":["实践"],
                  "interpretation":{
                    "inspiration":"先依据事实行动，再根据结果调整判断。",
                    "explanation":"认识必须回到实践中检验。"
                  },
                  "historicalEvent":"1937年7月，毛泽东在延安讲授哲学问题。",
                  "background":"抗日战争全面爆发前后，认识与实践问题受到集中讨论。",
                  "story":"讲授内容后来整理为《实践论》。",
                  "imageId":"$imageId",
                  "sources":[
                    {"name":"原文","url":"https://example.com/original","accessedAt":"2026-07-28","type":"original"},
                    {"name":"校核","url":"https://example.org/check","accessedAt":"2026-07-28","type":"authoritative"}
                  ],
                  "reviewedAt":"2026-07-28"
                }]}
            """.trimIndent()
        }
        val withdrawalsJson = if (withdrawalRevision == null) {
            """{"schemaVersion":4,"withdrawals":[]}"""
        } else {
            """{"schemaVersion":4,"withdrawals":[{"id":"$cardId","revision":$withdrawalRevision,"withdrawnAt":"2026-07-28"}]}"""
        }
        return zipOf(
            linkedMapOf(
                "package.json" to """{"schemaVersion":4,"contentVersion":"$contentVersion","publishedAt":"2026-07-28T00:00:00Z"}""".encodeToByteArray(),
                "cards.json" to cardsJson.encodeToByteArray(),
                "images.json" to """
                    {"schemaVersion":4,"images":[{
                      "id":"$imageId",
                      "localFile":"$assetName",
                      "sha256":"$imageHash",
                      "width":$declaredWidth,
                      "height":720,
                      "mimeType":"image/png",
                      "sourceUrl":"https://example.com/image",
                      "creator":"Integration fixture",
                      "license":"CC0-1.0",
                      "licenseEvidence":"https://creativecommons.org/publicdomain/zero/1.0/",
                      "verifiedAt":"2026-07-28",
                      "shareAllowed":true
                    }]}
                """.trimIndent().encodeToByteArray(),
                "withdrawals.json" to withdrawalsJson.encodeToByteArray(),
                assetName to imageBytes,
            ),
        )
    }

    private fun createPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(231, 225, 210))
        }
        return ByteArrayOutputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun zipOf(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
