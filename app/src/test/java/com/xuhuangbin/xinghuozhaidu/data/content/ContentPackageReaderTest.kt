package com.xuhuangbin.xinghuozhaidu.data.content

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContentPackageReaderTest {
    private val imageBytes = "fixture-image".encodeToByteArray()
    private val imageHash = imageBytes.sha256()

    @Test
    fun readsAValidStrictPackage() {
        val parsed = ContentPackageReader().read(validPackage())

        assertEquals("1.3.0", parsed.info.contentVersion)
        assertEquals(1, parsed.cards.size)
        assertEquals("先依据事实行动，再根据结果调整判断。", parsed.cards.single().interpretation.inspiration)
        assertEquals(imageBytes.toList(), parsed.assets.values.single().toList())
    }

    @Test
    fun acceptsBceCenturyAuthoredAt() {
        val entries = validEntries().withCardsJson {
            replace("\"authoredAt\":\"1937-07\"", "\"authoredAt\":\"前5世纪\"")
        }

        val parsed = ContentPackageReader().read(zipOf(entries))

        assertEquals("前5世纪", parsed.cards.single().authoredAt)
    }

    @Test
    fun readsCurrentBundledContentPackage() {
        val parsed = ContentPackageReader().read(
            File("src/main/assets/bootstrap.zip").readBytes(),
        )

        assertEquals("1.5.0", parsed.info.contentVersion)
        assertEquals(600, parsed.cards.size)
        assertEquals(
            "人的思维是否具有客观的真理性，这不是一个理论的问题，而是一个实践的问题。",
            parsed.cards.single {
                it.id == "a2291db4-b367-5a6d-a1ef-95a3a7ab90f9"
            }.quote,
        )
    }

    @Test
    fun rejectsZipSlipPaths() {
        val packageBytes = zipOf(mapOf("../package.json" to "{}".encodeToByteArray()))

        assertThrows(ContentPackageException::class.java) {
            ContentPackageReader().read(packageBytes)
        }
    }

    @Test
    fun rejectsFilesNotDeclaredByThePackage() {
        val entries = validEntries().toMutableMap()
        entries["assets/hidden.txt"] = "not declared".encodeToByteArray()

        val error = assertThrows(ContentPackageException::class.java) {
            ContentPackageReader().read(zipOf(entries))
        }

        assertTrueMessage(error.message, "未声明文件")
    }

    @Test
    fun rejectsPublishedAndWithdrawnConflict() {
        val entries = validEntries().toMutableMap()
        entries["withdrawals.json"] = """
            {"schemaVersion":3,"withdrawals":[{
              "id":"b85d8407-3b74-4c5e-b516-b032a22d73aa",
              "revision":2,
              "withdrawnAt":"2026-07-28"
            }]}
        """.trimIndent().encodeToByteArray()

        val error = assertThrows(ContentPackageException::class.java) {
            ContentPackageReader().read(zipOf(entries))
        }

        assertTrueMessage(error.message, "同时发布和下架")
    }

    @Test
    fun rejectsBlankInterpretationSection() {
        val entries = validEntries().toMutableMap()
        entries["cards.json"] = entries.getValue("cards.json")
            .decodeToString()
            .replace("先依据事实行动，再根据结果调整判断。", "   ")
            .encodeToByteArray()

        val error = assertThrows(ContentPackageException::class.java) {
            ContentPackageReader().read(zipOf(entries))
        }

        assertTrueMessage(error.message, "启示")
    }

    @Test
    fun rejectsInterpretationOverSixHundredCodePoints() {
        val entries = validEntries().toMutableMap()
        entries["cards.json"] = entries.getValue("cards.json")
            .decodeToString()
            .replace("认识必须回到实践中检验，并在行动中不断修正。", "实".repeat(601))
            .encodeToByteArray()

        val error = assertThrows(ContentPackageException::class.java) {
            ContentPackageReader().read(zipOf(entries))
        }

        assertTrueMessage(error.message, "超过 600")
    }

    @Test
    fun rejectsInspirationOverTwoHundredTwentyCodePoints() {
        val entries = validEntries().withCardsJson {
            replace("先依据事实行动，再根据结果调整判断。", "启".repeat(221))
        }

        val error = assertThrows(ContentPackageException::class.java) {
            ContentPackageReader().read(zipOf(entries))
        }

        assertTrueMessage(error.message, "启示超过 220")
    }

    @Test
    fun rejectsExplanationOverFourHundredTwentyCodePoints() {
        val entries = validEntries().withCardsJson {
            replace("认识必须回到实践中检验，并在行动中不断修正。", "解".repeat(421))
        }

        val error = assertThrows(ContentPackageException::class.java) {
            ContentPackageReader().read(zipOf(entries))
        }

        assertTrueMessage(error.message, "解读超过 420")
    }

    @Test
    fun rejectsHistoricalEventOverOneHundredCodePoints() {
        val entries = validEntries().withCardsJson {
            replace("1937年7月，毛泽东在延安讲授哲学问题。", "史".repeat(101))
        }

        val error = assertThrows(ContentPackageException::class.java) {
            ContentPackageReader().read(zipOf(entries))
        }

        assertTrueMessage(error.message, "历史事件无效")
    }

    @Test
    fun rejectsMissingBackground() {
        val entries = validEntries().withCardsJson {
            replace(
                "\"background\":\"抗日战争全面爆发前后，认识与实践问题受到集中讨论。\",",
                "",
            )
        }

        val error = assertThrows(ContentPackageException::class.java) {
            ContentPackageReader().read(zipOf(entries))
        }

        assertTrueMessage(error.message, "background")
    }

    @Test
    fun rejectsMissingStory() {
        val entries = validEntries().withCardsJson {
            replace("\"story\":\"讲授内容后来整理为《实践论》。\",", "")
        }

        val error = assertThrows(ContentPackageException::class.java) {
            ContentPackageReader().read(zipOf(entries))
        }

        assertTrueMessage(error.message, "story")
    }

    @Test
    fun rejectsRemovedContextExcerptField() {
        val entries = validEntries().withCardsJson {
            replace(
                "\"historicalEvent\":\"1937年7月，毛泽东在延安讲授哲学问题。\",",
                "\"historicalEvent\":\"1937年7月，毛泽东在延安讲授哲学问题。\"," +
                    "\"contextExcerpt\":\"已经从协议删除的旧字段\",",
            )
        }

        val error = assertThrows(ContentPackageException::class.java) {
            ContentPackageReader().read(zipOf(entries))
        }

        assertTrueMessage(error.message, "contextExcerpt")
    }

    private fun validPackage() = zipOf(validEntries())

    private fun Map<String, ByteArray>.withCardsJson(
        transform: String.() -> String,
    ): MutableMap<String, ByteArray> = toMutableMap().also { entries ->
        entries["cards.json"] = entries.getValue("cards.json")
            .decodeToString()
            .transform()
            .encodeToByteArray()
    }

    private fun validEntries(): Map<String, ByteArray> {
        val assetName = "assets/$imageHash.jpg"
        return linkedMapOf(
            "package.json" to """
                {"schemaVersion":3,"contentVersion":"1.3.0","publishedAt":"2026-07-29T00:00:00Z"}
            """.trimIndent().encodeToByteArray(),
            "cards.json" to """
                {"schemaVersion":3,"cards":[{
                  "id":"b85d8407-3b74-4c5e-b516-b032a22d73aa",
                  "revision":1,
                  "status":"published",
                  "quote":"实践是检验真理的标准。",
                  "series":"毛泽东选集",
                  "volume":"第一卷",
                  "workTitle":"实践论",
                  "authoredAt":"1937-07",
                  "themes":["实践"],
                  "interpretation":{
                    "inspiration":"先依据事实行动，再根据结果调整判断。",
                    "explanation":"认识必须回到实践中检验，并在行动中不断修正。"
                  },
                  "historicalEvent":"1937年7月，毛泽东在延安讲授哲学问题。",
                  "background":"抗日战争全面爆发前后，认识与实践问题受到集中讨论。",
                  "story":"讲授内容后来整理为《实践论》。",
                  "imageId":"paper",
                  "sources":[
                    {"name":"原文","url":"https://example.com/a","accessedAt":"2026-07-28","type":"original"},
                    {"name":"校核","url":"https://example.org/b","accessedAt":"2026-07-28","type":"authoritative"}
                  ],
                  "reviewedAt":"2026-07-28"
                }]}
            """.trimIndent().encodeToByteArray(),
            "images.json" to """
                {"schemaVersion":3,"images":[{
                  "id":"paper",
                  "localFile":"$assetName",
                  "sha256":"$imageHash",
                  "width":720,
                  "height":720,
                  "mimeType":"image/jpeg",
                  "sourceUrl":"https://example.com/image",
                  "creator":"Fixture",
                  "license":"CC0-1.0",
                  "licenseEvidence":"https://creativecommons.org/publicdomain/zero/1.0/",
                  "verifiedAt":"2026-07-28",
                  "shareAllowed":true
                }]}
            """.trimIndent().encodeToByteArray(),
            "withdrawals.json" to """{"schemaVersion":3,"withdrawals":[]}"""
                .encodeToByteArray(),
            assetName to imageBytes,
        )
    }

    private fun zipOf(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun assertTrueMessage(actual: String?, expectedPart: String) {
        check(actual?.contains(expectedPart) == true) {
            "Expected <$actual> to contain <$expectedPart>"
        }
    }
}
