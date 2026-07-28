package com.xuhuangbin.xinghuozhaidu.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.room.withTransaction
import com.xuhuangbin.xinghuozhaidu.data.content.CardDto
import com.xuhuangbin.xinghuozhaidu.data.content.ContentPackageReader
import com.xuhuangbin.xinghuozhaidu.data.content.ContentVersion
import com.xuhuangbin.xinghuozhaidu.data.content.ParsedContentPackage
import com.xuhuangbin.xinghuozhaidu.data.local.AppDao
import com.xuhuangbin.xinghuozhaidu.data.local.CardEntity
import com.xuhuangbin.xinghuozhaidu.data.local.CardSourceEntity
import com.xuhuangbin.xinghuozhaidu.data.local.CardWithSources
import com.xuhuangbin.xinghuozhaidu.data.local.ContentStateEntity
import com.xuhuangbin.xinghuozhaidu.data.local.ImageAssetEntity
import com.xuhuangbin.xinghuozhaidu.data.local.NoteEntity
import com.xuhuangbin.xinghuozhaidu.data.local.ReadingRoundEntity
import com.xuhuangbin.xinghuozhaidu.data.local.ReadingRoundItemEntity
import com.xuhuangbin.xinghuozhaidu.data.local.SearchHistoryEntity
import com.xuhuangbin.xinghuozhaidu.data.local.UserCardStateEntity
import com.xuhuangbin.xinghuozhaidu.data.local.WithdrawalEntity
import com.xuhuangbin.xinghuozhaidu.data.local.XinghuoDatabase
import com.xuhuangbin.xinghuozhaidu.data.network.ContentUpdateClient
import com.xuhuangbin.xinghuozhaidu.domain.model.CardSource
import com.xuhuangbin.xinghuozhaidu.domain.model.CardInterpretation
import com.xuhuangbin.xinghuozhaidu.domain.model.InstalledContentState
import com.xuhuangbin.xinghuozhaidu.domain.model.PersonalNote
import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard
import com.xuhuangbin.xinghuozhaidu.domain.model.ReaderState
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AppRepository(
    private val context: Context,
    private val database: XinghuoDatabase,
    private val packageReader: ContentPackageReader = ContentPackageReader(),
    private val updateClient: ContentUpdateClient = ContentUpdateClient(),
    private val random: Random = Random.Default,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao: AppDao = database.appDao()
    private val initializationMutex = Mutex()

    val allCards: Flow<List<QuoteCard>> = combine(
        dao.observeCards(),
        dao.observeImages(),
        dao.observeUserStates(),
    ) { cards, images, states ->
        val imagePaths = images.associate { it.id to it.localPath }
        val stateByCard = states.associateBy { it.cardId }
        cards.map { value -> value.toDomain(imagePaths, stateByCard[value.card.id]) }
    }

    val activeCards: Flow<List<QuoteCard>> = allCards.map { cards ->
        cards.filterNot(QuoteCard::isWithdrawn)
    }

    val favorites: Flow<List<QuoteCard>> = allCards.map { cards ->
        cards.filter(QuoteCard::isFavorited).sortedByDescending { it.favoritedAt ?: 0L }
    }

    val liked: Flow<List<QuoteCard>> = allCards.map { cards ->
        cards.filter(QuoteCard::isLiked).sortedByDescending { it.likedAt ?: 0L }
    }

    val searchHistory: Flow<List<String>> = dao.observeSearchHistory().map { entries ->
        entries.map(SearchHistoryEntity::keyword)
    }

    val notes: Flow<List<PersonalNote>> = dao.observeNotes().map { entries ->
        entries.map { entry -> entry.toDomain() }
    }

    val contentState: Flow<InstalledContentState?> = dao.observeContentState().map { state ->
        state?.let {
            InstalledContentState(
                contentVersion = it.contentVersion,
                publishedAt = it.publishedAt,
                lastCheckedAt = it.lastCheckedAt,
                lastUpdatedAt = it.lastUpdatedAt,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val readerState: Flow<ReaderState> = dao.observeActiveRound().flatMapLatest { round ->
        if (round == null) {
            flowOf(ReaderState())
        } else {
            combine(allCards, dao.observeRoundItems(round.id)) { cards, items ->
                val cardById = cards.filterNot(QuoteCard::isWithdrawn).associateBy(QuoteCard::id)
                val ordered = items.mapNotNull { item -> cardById[item.cardId] }
                val readIds = items.filter { it.readAt != null }.mapTo(mutableSetOf()) { it.cardId }
                ReaderState(
                    roundId = round.id,
                    cards = ordered,
                    readCardIds = readIds,
                    currentIndex = round.currentPosition.coerceIn(0, (ordered.size - 1).coerceAtLeast(0)),
                    isComplete = ordered.isNotEmpty() && ordered.all { it.id in readIds },
                )
            }
        }
    }

    suspend fun initialize() = initializationMutex.withLock {
        withContext(Dispatchers.IO) {
            val bytes = context.assets.open("bootstrap.zip").use { it.readBytes() }
            val bundledVersion = packageReader.read(bytes).info.contentVersion
            val installedVersion = dao.getContentState()?.contentVersion
            if (installedVersion == null ||
                ContentVersion.compare(bundledVersion, installedVersion) > 0
            ) {
                importPackage(bytes)
            }
            ensureActiveRound()
            cleanupOrphanedAssets()
        }
    }

    suspend fun importPackage(
        bytes: ByteArray,
        expectedSha256: String? = null,
        expectedContentVersion: String? = null,
        expectedPublishedAt: String? = null,
        requireNewerVersion: Boolean = false,
    ) {
        withContext(Dispatchers.IO) {
            if (expectedSha256 != null && !bytes.sha256().equals(expectedSha256, ignoreCase = true)) {
                error("内容包完整性校验失败")
            }
            val parsed = packageReader.read(bytes)
            if (expectedContentVersion != null && parsed.info.contentVersion != expectedContentVersion) {
                error("版本清单与内容包版本不一致")
            }
            if (expectedPublishedAt != null && parsed.info.publishedAt != expectedPublishedAt) {
                error("版本清单与内容包发布日期不一致")
            }
            val installed = dao.getContentState()
            if (installed != null) {
                val comparison = ContentVersion.compare(parsed.info.contentVersion, installed.contentVersion)
                require(comparison >= 0) { "不能安装旧于当前版本的内容包" }
                if (requireNewerVersion) {
                    require(comparison > 0) { "远端内容版本没有更新" }
                }
            }
            validateDecodedAssets(parsed)
            try {
                val imagePaths = writeAssets(parsed)
                val unusedAssetPaths = database.withTransaction {
                    applyPackage(parsed, imagePaths)
                    reconcileActiveRound()
                    pruneUnreferencedImages()
                }
                unusedAssetPaths.forEach(::deleteManagedAsset)
            } finally {
                cleanupOrphanedAssets()
            }
        }
    }

    suspend fun checkForUpdate(manifestUrl: String): com.xuhuangbin.xinghuozhaidu.data.content.RemoteManifestDto? {
        val manifest = updateClient.fetchManifest(manifestUrl)
        dao.updateLastCheckedAt(now())
        val installed = dao.getContentState()?.contentVersion ?: "0.0.0"
        return manifest.takeIf { ContentVersion.compare(it.contentVersion, installed) > 0 }
    }

    suspend fun downloadAndInstall(
        manifest: com.xuhuangbin.xinghuozhaidu.data.content.RemoteManifestDto,
        onProgress: (Float) -> Unit,
    ) {
        val bytes = updateClient.downloadPackage(manifest, onProgress)
        importPackage(
            bytes = bytes,
            expectedSha256 = manifest.packageSha256,
            expectedContentVersion = manifest.contentVersion,
            expectedPublishedAt = manifest.publishedAt,
            requireNewerVersion = true,
        )
    }

    suspend fun updatePosition(index: Int) {
        val round = dao.getActiveRound() ?: return
        val max = (dao.getRoundItems(round.id).size - 1).coerceAtLeast(0)
        dao.updateRoundPosition(round.id, index.coerceIn(0, max))
    }

    suspend fun markRead(cardId: String) {
        val round = dao.getActiveRound() ?: return
        dao.markRead(round.id, cardId, now())
        val items = dao.getRoundItems(round.id)
        val activeIds = dao.getActiveCardIds().toSet()
        val activeItems = items.filter { it.cardId in activeIds }
        if (activeItems.isNotEmpty() && activeItems.all { it.readAt != null }) {
            dao.completeRound(round.id, now())
        }
    }

    suspend fun startNewRound() = database.withTransaction {
        dao.archiveRounds()
        createRound(dao.getActiveCardIds())
    }

    suspend fun toggleLike(cardId: String) {
        updateUserState(cardId) { current ->
            val liked = !current.liked
            current.copy(liked = liked, likedAt = if (liked) now() else null)
        }
    }

    suspend fun toggleFavorite(cardId: String) {
        updateUserState(cardId) { current ->
            val favorited = !current.favorited
            current.copy(
                favorited = favorited,
                favoritedAt = if (favorited) now() else null,
            )
        }
    }

    fun search(query: String): Flow<List<QuoteCard>> = activeCards.map { cards ->
        val keyword = query.trim()
        if (keyword.isEmpty()) emptyList()
        else cards.filter { it.quote.contains(keyword, ignoreCase = true) || it.workTitle.contains(keyword, ignoreCase = true) }
    }

    suspend fun saveSearchQuery(query: String) = withContext(Dispatchers.IO) {
        val keyword = query.trim()
        if (keyword.isEmpty()) return@withContext
        database.withTransaction {
            dao.insertSearchHistory(SearchHistoryEntity(keyword = keyword, searchedAt = now()))
            dao.trimSearchHistory(MAX_SEARCH_HISTORY)
        }
    }

    suspend fun deleteSearchHistory(keyword: String) = withContext(Dispatchers.IO) {
        dao.deleteSearchHistory(keyword)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        dao.clearSearchHistory()
    }

    suspend fun saveNote(
        noteId: Long?,
        cardId: String?,
        title: String,
        body: String,
    ): Long = withContext(Dispatchers.IO) {
        val normalizedTitle = title.trim().takeIf(String::isNotEmpty)
        val normalizedBody = body.trim()
        require(normalizedBody.isNotEmpty()) { "笔记正文不能为空" }
        database.withTransaction {
            if (noteId == null) {
                if (cardId != null) requireNotNull(dao.getCard(cardId)) { "关联卡片不存在" }
                val timestamp = now()
                dao.insertNote(
                    NoteEntity(
                        cardId = cardId,
                        title = normalizedTitle,
                        body = normalizedBody,
                        createdAt = timestamp,
                        updatedAt = timestamp,
                    ),
                )
            } else {
                val existing = requireNotNull(dao.getNote(noteId)) { "笔记不存在" }
                require(existing.cardId == cardId) { "不能修改笔记关联的卡片" }
                check(dao.updateNote(noteId, normalizedTitle, normalizedBody, now()) == 1)
                noteId
            }
        }
    }

    suspend fun deleteNote(noteId: Long) = withContext(Dispatchers.IO) {
        val unusedAssetPaths = database.withTransaction {
            val note = dao.getNote(noteId) ?: return@withTransaction emptyList()
            dao.deleteNote(noteId)
            note.cardId?.let { removeWithdrawnSnapshotIfUnreferenced(it) }
            pruneUnreferencedImages()
        }
        unusedAssetPaths.forEach(::deleteManagedAsset)
    }

    private fun writeAssets(parsed: ParsedContentPackage): Map<String, String> {
        val assetRoot = File(context.filesDir, "content/assets").apply { mkdirs() }
        return parsed.images.associate { image ->
            val extension = image.localFile.substringAfterLast('.', "bin")
            val destination = File(assetRoot, "${image.sha256}.$extension")
            if (!destination.exists() || !destination.readBytes().sha256().equals(image.sha256, true)) {
                val bytes = parsed.assets.getValue(image.localFile)
                val temporary = File(assetRoot, "${destination.name}.tmp")
                temporary.writeBytes(bytes)
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
            }
            image.id to destination.absolutePath
        }
    }

    private fun validateDecodedAssets(parsed: ParsedContentPackage) {
        parsed.images.forEach { image ->
            val bytes = parsed.assets.getValue(image.localFile)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            require(options.outWidth > 0 && options.outHeight > 0) {
                "图片 ${image.id} 无法解码"
            }
            require(options.outWidth == image.width && options.outHeight == image.height) {
                "图片 ${image.id} 的实际尺寸与声明不一致"
            }
            require(options.outMimeType.equals(image.mimeType, ignoreCase = true)) {
                "图片 ${image.id} 的实际 MIME 类型与声明不一致"
            }
        }
    }

    private suspend fun applyPackage(parsed: ParsedContentPackage, imagePaths: Map<String, String>) {
        val existingActive = dao.getActiveCardIds().toSet()
        val newIds = parsed.cards.mapTo(mutableSetOf()) { it.id }
        val withdrawalIds = parsed.withdrawals.mapTo(mutableSetOf()) { it.id }
        val silentlyMissing = existingActive - newIds - withdrawalIds
        require(silentlyMissing.isEmpty()) { "内容包缺少显式下架记录：$silentlyMissing" }

        val existingImages = dao.getImages().associateBy { it.id }
        parsed.images.forEach { image ->
            val existing = existingImages[image.id]
            require(existing == null || existing.sha256.equals(image.sha256, ignoreCase = true)) {
                "图片 ${image.id} 已存在，不能复用 ID 替换文件"
            }
        }
        dao.upsertImages(parsed.images.map { image ->
            ImageAssetEntity(
                id = image.id,
                sha256 = image.sha256,
                localPath = imagePaths.getValue(image.id),
                width = image.width,
                height = image.height,
                mimeType = image.mimeType,
                sourceUrl = image.sourceUrl,
                creator = image.creator,
                licenseName = image.license,
                licenseEvidence = image.licenseEvidence,
                verifiedAt = image.verifiedAt,
            )
        })
        parsed.cards.forEach { card ->
            val previous = dao.getCardWithSources(card.id)
            val previousWithdrawal = dao.getWithdrawal(card.id)
            require(previousWithdrawal == null || card.revision > previousWithdrawal.revision) {
                "卡片 ${card.id} 的 revision 未高于历史下架版本"
            }
            val entity = card.toEntity()
            val sources = card.toSourceEntities()
            require(previous == null || card.revision >= previous.card.revision) {
                "卡片 ${card.id} 的 revision 发生倒退"
            }
            if (previous != null && card.revision == previous.card.revision) {
                require(previous.card == entity && previous.sources.sortedBy { it.position } == sources) {
                    "卡片 ${card.id} 在未提升 revision 时发生内容变化"
                }
            }
            dao.upsertCards(listOf(entity))
            dao.deleteSources(card.id)
            dao.insertSources(sources)
        }
        parsed.withdrawals.forEach { withdrawal ->
            val existingCard = dao.getCard(withdrawal.id)
            val existingWithdrawal = dao.getWithdrawal(withdrawal.id)
            require(existingCard == null || existingCard.availability == "withdrawn" ||
                withdrawal.revision > existingCard.revision
            ) { "下架卡片 ${withdrawal.id} 的 revision 未提升" }
            require(existingWithdrawal == null || withdrawal.revision >= existingWithdrawal.revision) {
                "下架卡片 ${withdrawal.id} 的 revision 发生倒退"
            }
            if (existingWithdrawal != null && withdrawal.revision == existingWithdrawal.revision) {
                require(withdrawal.withdrawnAt == existingWithdrawal.withdrawnAt) {
                    "下架卡片 ${withdrawal.id} 在未提升 revision 时发生变化"
                }
            }
            dao.upsertWithdrawals(
                listOf(WithdrawalEntity(withdrawal.id, withdrawal.revision, withdrawal.withdrawnAt)),
            )
            val userState = dao.getUserState(withdrawal.id)
            if (userState?.liked == true ||
                userState?.favorited == true ||
                dao.countNotesForCard(withdrawal.id) > 0
            ) {
                dao.markWithdrawn(withdrawal.id)
            } else {
                dao.deleteCard(withdrawal.id)
                dao.deleteUserState(withdrawal.id)
            }
        }
        val previousContentState = dao.getContentState()
        dao.upsertContentState(
            ContentStateEntity(
                contentVersion = parsed.info.contentVersion,
                publishedAt = parsed.info.publishedAt,
                lastCheckedAt = previousContentState?.lastCheckedAt,
                lastUpdatedAt = now(),
            ),
        )
    }

    private suspend fun ensureActiveRound() {
        if (dao.getActiveRound() == null) {
            database.withTransaction { createRound(dao.getActiveCardIds()) }
        }
    }

    private suspend fun createRound(cardIds: List<String>) {
        if (cardIds.isEmpty()) return
        val shuffled = cardIds.shuffled(random)
        val roundId = dao.insertRound(
            ReadingRoundEntity(
                state = "active",
                currentPosition = 0,
                createdAt = now(),
            ),
        )
        dao.insertRoundItems(shuffled.mapIndexed { index, cardId ->
            ReadingRoundItemEntity(roundId = roundId, position = index, cardId = cardId)
        })
    }

    private suspend fun reconcileActiveRound() {
        val round = dao.getActiveRound() ?: run {
            createRound(dao.getActiveCardIds())
            return
        }
        val activeIds = dao.getActiveCardIds().toSet()
        val existingItems = dao.getRoundItems(round.id)
        val plan = ReadingRoundPlanner.reconcile(
            roundId = round.id,
            existingItems = existingItems,
            currentPosition = round.currentPosition,
            activeCardIds = activeIds,
            random = random,
        )
        if (plan.items == existingItems && plan.currentPosition == round.currentPosition) return
        dao.deleteRoundItems(round.id)
        dao.insertRoundItems(plan.items)
        dao.updateRoundPosition(round.id, plan.currentPosition)
        if (plan.addedCardIds.isNotEmpty() && round.state == "completed") dao.reactivateRound(round.id)
    }

    private suspend fun updateUserState(
        cardId: String,
        transform: (UserCardStateEntity) -> UserCardStateEntity,
    ) = withContext(Dispatchers.IO) {
        val unusedAssetPaths = database.withTransaction {
            val current = dao.getUserState(cardId) ?: UserCardStateEntity(cardId)
            val updated = transform(current)
            dao.upsertUserState(updated)
            removeWithdrawnSnapshotIfUnreferenced(cardId)
            pruneUnreferencedImages()
        }
        unusedAssetPaths.forEach(::deleteManagedAsset)
    }

    private suspend fun removeWithdrawnSnapshotIfUnreferenced(cardId: String) {
        val card = dao.getCard(cardId) ?: return
        if (card.availability != "withdrawn") return
        val state = dao.getUserState(cardId)
        if (state?.liked == true || state?.favorited == true || dao.countNotesForCard(cardId) > 0) return
        dao.deleteUserState(cardId)
        dao.deleteCard(cardId)
    }

    private suspend fun pruneUnreferencedImages(): List<String> {
        val referencedIds = dao.getReferencedImageIds().toSet()
        return dao.getImages().filterNot { it.id in referencedIds }.map { image ->
            dao.deleteImage(image.id)
            image.localPath
        }
    }

    private suspend fun cleanupOrphanedAssets() {
        val root = File(context.filesDir, "content/assets").canonicalFile
        if (!root.exists()) return
        val referencedPaths = dao.getImages().mapTo(mutableSetOf()) { image ->
            File(image.localPath).canonicalPath
        }
        root.listFiles()
            ?.asSequence()
            ?.filter(File::isFile)
            ?.filter { file -> file.canonicalPath !in referencedPaths }
            ?.forEach(File::delete)
    }

    private fun deleteManagedAsset(path: String) {
        val root = File(context.filesDir, "content/assets").canonicalFile
        val file = File(path).canonicalFile
        if (file.parentFile == root) file.delete()
    }

    private fun CardDto.toEntity() = CardEntity(
        id = id,
        revision = revision,
        quote = quote,
        series = series,
        volume = volume,
        workTitle = workTitle,
        authoredAt = authoredAt,
        themes = themes.joinToString("\u001f"),
        interpretationCoreMeaning = interpretation.coreMeaning,
        interpretationKeyPoint = interpretation.keyPoint,
        interpretationContemporaryRelevance = interpretation.contemporaryRelevance,
        contextExcerpt = contextExcerpt,
        background = background,
        story = story,
        imageId = imageId,
        availability = "active",
    )

    private fun CardDto.toSourceEntities() = sources.mapIndexed { index, source ->
        CardSourceEntity(
            cardId = id,
            position = index,
            name = source.name,
            url = source.url,
            accessedAt = source.accessedAt,
            evidenceType = source.type,
        )
    }

    private fun CardWithSources.toDomain(
        imagePaths: Map<String, String>,
        state: UserCardStateEntity?,
    ): QuoteCard = QuoteCard(
        id = card.id,
        revision = card.revision,
        quote = card.quote,
        series = card.series,
        volume = card.volume,
        workTitle = card.workTitle,
        authoredAt = card.authoredAt,
        themes = card.themes.split("\u001f").filter(String::isNotBlank),
        interpretation = CardInterpretation(
            coreMeaning = card.interpretationCoreMeaning,
            keyPoint = card.interpretationKeyPoint,
            contemporaryRelevance = card.interpretationContemporaryRelevance,
        ),
        contextExcerpt = card.contextExcerpt,
        background = card.background,
        story = card.story,
        imagePath = imagePaths[card.imageId].orEmpty(),
        sources = sources.sortedBy { it.position }.map { source ->
            CardSource(source.name, source.url, source.accessedAt, source.evidenceType)
        },
        isWithdrawn = card.availability == "withdrawn",
        isLiked = state?.liked == true,
        isFavorited = state?.favorited == true,
        likedAt = state?.likedAt,
        favoritedAt = state?.favoritedAt,
    )

    private fun NoteEntity.toDomain() = PersonalNote(
        id = id,
        cardId = cardId,
        title = title,
        body = body,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val MAX_SEARCH_HISTORY = 10
    }

}
