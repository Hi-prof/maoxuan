package com.xuhuangbin.xinghuozhaidu.data.content

import java.io.ByteArrayInputStream
import java.net.URI
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json

class ContentPackageException(message: String) : IllegalArgumentException(message)

class ContentPackageReader(
    private val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    },
) {
    fun read(packageBytes: ByteArray): ParsedContentPackage {
        if (packageBytes.size > MAX_PACKAGE_BYTES) {
            throw ContentPackageException("内容包超过 50 MiB 限制")
        }
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(packageBytes)).use { zip ->
            var entryCount = 0
            var totalBytes = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw ContentPackageException("内容包文件数量超过限制")
                }
                val name = normalizeEntryName(entry.name)
                if (entry.isDirectory) continue
                val bytes = zip.readBytesLimited(minOf(MAX_ENTRY_BYTES, MAX_TOTAL_BYTES - totalBytes))
                totalBytes += bytes.size
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw ContentPackageException("内容包解压后超过限制")
                }
                if (entries.put(name, bytes) != null) {
                    throw ContentPackageException("内容包包含重复文件：$name")
                }
            }
        }

        val info = decode<PackageInfoDto>(entries, "package.json")
        val cards = decode<CardsEnvelopeDto>(entries, "cards.json")
        val images = decode<ImagesEnvelopeDto>(entries, "images.json")
        val withdrawals = decode<WithdrawalsEnvelopeDto>(entries, "withdrawals.json")
        requireSchema(info.schemaVersion)
        requireSchema(cards.schemaVersion)
        requireSchema(images.schemaVersion)
        requireSchema(withdrawals.schemaVersion)

        ContentVersion.requireValid(info.contentVersion)
        requireInstant(info.publishedAt, "内容发布日期")
        requireUnique(cards.cards.map(CardDto::id), "卡片 ID")
        requireUnique(images.images.map(ImageDto::id), "图片 ID")
        requireUnique(withdrawals.withdrawals.map(WithdrawalDto::id), "下架卡片 ID")
        val cardIds = cards.cards.mapTo(mutableSetOf(), CardDto::id)
        val withdrawalIds = withdrawals.withdrawals.mapTo(mutableSetOf(), WithdrawalDto::id)
        val conflictingIds = cardIds intersect withdrawalIds
        if (conflictingIds.isNotEmpty()) {
            throw ContentPackageException("同一内容包不能同时发布和下架卡片：$conflictingIds")
        }

        images.images.forEach(::validateImage)
        cards.cards.forEach(::validateCard)
        withdrawals.withdrawals.forEach(::validateWithdrawal)

        val assets = images.images.associate { image ->
            val bytes = entries[image.localFile]
                ?: throw ContentPackageException("缺少图片文件：${image.localFile}")
            val actualHash = bytes.sha256()
            if (!actualHash.equals(image.sha256, ignoreCase = true)) {
                throw ContentPackageException("图片 ${image.id} 的 SHA-256 不匹配")
            }
            image.localFile to bytes
        }
        val imageIds = images.images.mapTo(mutableSetOf()) { it.id }
        cards.cards.forEach { card ->
            if (card.imageId !in imageIds) {
                throw ContentPackageException("卡片 ${card.id} 引用了不存在的图片")
            }
        }
        val expectedEntries = buildSet {
            addAll(REQUIRED_JSON_ENTRIES)
            addAll(images.images.map(ImageDto::localFile))
        }
        val unexpectedEntries = entries.keys - expectedEntries
        if (unexpectedEntries.isNotEmpty()) {
            throw ContentPackageException("内容包包含未声明文件：$unexpectedEntries")
        }
        return ParsedContentPackage(
            info = info,
            cards = cards.cards,
            images = images.images,
            withdrawals = withdrawals.withdrawals,
            assets = assets,
        )
    }

    private inline fun <reified T> decode(entries: Map<String, ByteArray>, name: String): T {
        val bytes = entries[name] ?: throw ContentPackageException("缺少 $name")
        return try {
            json.decodeFromString<T>(bytes.decodeToString())
        } catch (error: Exception) {
            throw ContentPackageException("无法解析 $name：${error.message}")
        }
    }

    private fun normalizeEntryName(rawName: String): String {
        val name = rawName.replace('\\', '/')
        if (name.startsWith('/') || name.split('/').any { it == ".." || it.isBlank() }) {
            throw ContentPackageException("内容包包含非法路径：$rawName")
        }
        return name
    }

    private fun requireSchema(value: Int) {
        if (value != SUPPORTED_SCHEMA) {
            throw ContentPackageException("不支持的内容 schema：$value")
        }
    }

    private fun validateImage(image: ImageDto) {
        requireText(image.id, "图片 ID")
        requireText(image.sha256, "图片 ${image.id} 的 SHA-256")
        if (!SHA_256.matches(image.sha256)) {
            throw ContentPackageException("图片 ${image.id} 的 SHA-256 格式无效")
        }
        if (!image.localFile.startsWith("assets/") || image.localFile.count { it == '/' } != 1) {
            throw ContentPackageException("图片 ${image.id} 的包内路径无效")
        }
        val extension = image.localFile.substringAfterLast('.', "").lowercase()
        if (MIME_TYPES[extension] != image.mimeType) {
            throw ContentPackageException("图片 ${image.id} 的扩展名与 MIME 类型不匹配")
        }
        if (image.width !in MIN_IMAGE_EDGE..MAX_IMAGE_EDGE ||
            image.height !in MIN_IMAGE_EDGE..MAX_IMAGE_EDGE ||
            image.width.toLong() * image.height > MAX_IMAGE_PIXELS
        ) {
            throw ContentPackageException("图片 ${image.id} 的尺寸无效")
        }
        if (!image.shareAllowed) {
            throw ContentPackageException("图片 ${image.id} 不允许分享图再分发")
        }
        requireHttpUrl(image.sourceUrl, "图片 ${image.id} 的来源")
        requireHttpUrl(image.licenseEvidence, "图片 ${image.id} 的许可依据")
        requireText(image.creator, "图片 ${image.id} 的作者")
        requireText(image.license, "图片 ${image.id} 的许可")
        requireDate(image.verifiedAt, "图片 ${image.id} 的核验日期")
    }

    private fun validateCard(card: CardDto) {
        requireUuid(card.id, "卡片 ID")
        if (card.revision < 1) throw ContentPackageException("卡片 ${card.id} 的 revision 无效")
        if (card.status != "published") {
            throw ContentPackageException("发布包中的卡片 ${card.id} 状态必须为 published")
        }
        val quote = requireText(card.quote, "卡片 ${card.id} 的正文")
        if (quote.contains('\n') || quote.contains('\r') ||
            quote.codePointCount(0, quote.length) > MAX_QUOTE_CODE_POINTS ||
            !Normalizer.isNormalized(quote, Normalizer.Form.NFC)
        ) {
            throw ContentPackageException("卡片 ${card.id} 的正文无效")
        }
        requireText(card.series, "卡片 ${card.id} 的文献系列")
        requireText(card.volume, "卡片 ${card.id} 的卷次")
        requireText(card.workTitle, "卡片 ${card.id} 的篇名")
        if (!PARTIAL_DATE.matches(card.authoredAt)) {
            throw ContentPackageException("卡片 ${card.id} 的写作时间无效")
        }
        if (card.themes.isEmpty() || card.themes.any(String::isBlank)) {
            throw ContentPackageException("卡片 ${card.id} 至少需要一个主题")
        }
        val interpretationParts = listOf(
            "核心意思" to card.interpretation.coreMeaning,
            "理解重点" to card.interpretation.keyPoint,
            "现实启示" to card.interpretation.contemporaryRelevance,
        )
        interpretationParts.forEach { (label, value) ->
            requireText(value, "卡片 ${card.id} 的解读·$label")
            if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
                throw ContentPackageException("卡片 ${card.id} 的解读·$label 不是 NFC 文本")
            }
        }
        val interpretationCodePoints = interpretationParts.sumOf { (_, value) ->
            value.codePointCount(0, value.length)
        }
        if (interpretationCodePoints > MAX_INTERPRETATION_CODE_POINTS) {
            throw ContentPackageException("卡片 ${card.id} 的解读超过 600 个字符")
        }
        listOf(card.contextExcerpt, card.background, card.story).forEach { optionalText ->
            if (optionalText != null && optionalText.isBlank()) {
                throw ContentPackageException("卡片 ${card.id} 包含空白选填内容")
            }
        }
        requireText(card.imageId, "卡片 ${card.id} 的图片 ID")
        if (card.sources.size < 2) {
            throw ContentPackageException("卡片 ${card.id} 缺少双源核验")
        }
        val sourceUrls = mutableSetOf<String>()
        card.sources.forEachIndexed { index, source ->
            requireText(source.name, "卡片 ${card.id} 的来源 ${index + 1} 名称")
            requireHttpUrl(source.url, "卡片 ${card.id} 的来源 ${index + 1}")
            requireDate(source.accessedAt, "卡片 ${card.id} 的来源 ${index + 1} 访问日期")
            if (source.type !in EVIDENCE_TYPES) {
                throw ContentPackageException("卡片 ${card.id} 的来源证据类型无效")
            }
            if (!sourceUrls.add(source.url)) {
                throw ContentPackageException("卡片 ${card.id} 的来源 URL 必须互不相同")
            }
        }
        if (card.sources.none { it.type == "original" || it.type == "authoritative" }) {
            throw ContentPackageException("卡片 ${card.id} 缺少原文或权威来源")
        }
        requireDate(card.reviewedAt, "卡片 ${card.id} 的核验日期")
    }

    private fun validateWithdrawal(withdrawal: WithdrawalDto) {
        requireUuid(withdrawal.id, "下架卡片 ID")
        if (withdrawal.revision < 1) {
            throw ContentPackageException("下架卡片 ${withdrawal.id} 的 revision 无效")
        }
        requireDate(withdrawal.withdrawnAt, "下架卡片 ${withdrawal.id} 的下架日期")
    }

    private fun requireText(value: String, label: String): String {
        if (value.isBlank() || value != value.trim()) {
            throw ContentPackageException("$label 不能为空或包含首尾空白")
        }
        return value
    }

    private fun requireUuid(value: String, label: String) {
        try {
            UUID.fromString(requireText(value, label))
        } catch (_: IllegalArgumentException) {
            throw ContentPackageException("$label 必须是 UUID")
        }
    }

    private fun requireDate(value: String, label: String) {
        try {
            LocalDate.parse(value)
        } catch (_: Exception) {
            throw ContentPackageException("$label 必须使用 YYYY-MM-DD")
        }
    }

    private fun requireInstant(value: String, label: String) {
        try {
            Instant.parse(value)
        } catch (_: Exception) {
            throw ContentPackageException("$label 必须是 UTC ISO-8601 时间")
        }
    }

    private fun requireHttpUrl(value: String, label: String) {
        val uri = try {
            URI(requireText(value, label))
        } catch (_: Exception) {
            throw ContentPackageException("$label 不是有效 URL")
        }
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            throw ContentPackageException("$label 必须是 HTTP(S) URL")
        }
    }

    private fun requireUnique(values: List<String>, label: String) {
        val duplicates = values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw ContentPackageException("$label 重复：$duplicates")
        }
    }

    private fun ZipInputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw ContentPackageException("内容包单文件超过限制")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val SUPPORTED_SCHEMA = 2
        const val MAX_PACKAGE_BYTES = 50 * 1024 * 1024
        const val MAX_ENTRY_BYTES = 10 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 75 * 1024 * 1024
        const val MAX_ENTRY_COUNT = 1_000
        const val MAX_QUOTE_CODE_POINTS = 90
        const val MAX_INTERPRETATION_CODE_POINTS = 600
        const val MIN_IMAGE_EDGE = 720
        const val MAX_IMAGE_EDGE = 8_192
        const val MAX_IMAGE_PIXELS = 40_000_000L
        val SHA_256 = Regex("^[0-9a-fA-F]{64}$")
        val PARTIAL_DATE = Regex("^\\d{4}(?:-\\d{2}(?:-\\d{2})?)?$")
        val EVIDENCE_TYPES = setOf("original", "authoritative", "contextual")
        val MIME_TYPES = mapOf(
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "webp" to "image/webp",
        )
        val REQUIRED_JSON_ENTRIES = setOf(
            "package.json",
            "cards.json",
            "images.json",
            "withdrawals.json",
        )
    }
}
