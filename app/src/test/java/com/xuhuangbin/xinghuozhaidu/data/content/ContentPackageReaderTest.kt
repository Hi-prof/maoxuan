package com.xuhuangbin.xinghuozhaidu.data.content

import java.io.ByteArrayOutputStream
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

        assertEquals("1.1.0", parsed.info.contentVersion)
        assertEquals(1, parsed.cards.size)
        assertEquals("认识必须回到实践中检验。", parsed.cards.single().interpretation.coreMeaning)
        assertEquals(imageBytes.toList(), parsed.assets.values.single().toList())
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
            {"schemaVersion":2,"withdrawals":[{
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
            .replace("认识必须回到实践中检验。", "   ")
            .encodeToByteArray()

        val error = assertThrows(ContentPackageException::class.java) {
            ContentPackageReader().read(zipOf(entries))
        }

        assertTrueMessage(error.message, "核心意思")
    }

    @Test
    fun rejectsInterpretationOverSixHundredCodePoints() {
        val entries = validEntries().toMutableMap()
        entries["cards.json"] = entries.getValue("cards.json")
            .decodeToString()
            .replace("认识必须回到实践中检验。", "实".repeat(601))
            .encodeToByteArray()

        val error = assertThrows(ContentPackageException::class.java) {
            ContentPackageReader().read(zipOf(entries))
        }

        assertTrueMessage(error.message, "超过 600")
    }

    private fun validPackage() = zipOf(validEntries())

    private fun validEntries(): Map<String, ByteArray> {
        val assetName = "assets/$imageHash.jpg"
        return linkedMapOf(
            "package.json" to """
                {"schemaVersion":2,"contentVersion":"1.1.0","publishedAt":"2026-07-28T00:00:00Z"}
            """.trimIndent().encodeToByteArray(),
            "cards.json" to """
                {"schemaVersion":2,"cards":[{
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
                    "coreMeaning":"认识必须回到实践中检验。",
                    "keyPoint":"实践和理论要在反复验证中相互修正。",
                    "contemporaryRelevance":"先依据事实行动，再根据结果调整判断。"
                  },
                  "imageId":"paper",
                  "sources":[
                    {"name":"原文","url":"https://example.com/a","accessedAt":"2026-07-28","type":"original"},
                    {"name":"校核","url":"https://example.org/b","accessedAt":"2026-07-28","type":"authoritative"}
                  ],
                  "reviewedAt":"2026-07-28"
                }]}
            """.trimIndent().encodeToByteArray(),
            "images.json" to """
                {"schemaVersion":2,"images":[{
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
            "withdrawals.json" to """{"schemaVersion":2,"withdrawals":[]}"""
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
