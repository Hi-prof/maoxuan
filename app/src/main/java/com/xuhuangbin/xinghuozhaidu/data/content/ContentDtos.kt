package com.xuhuangbin.xinghuozhaidu.data.content

import kotlinx.serialization.Serializable

@Serializable
data class PackageInfoDto(
    val schemaVersion: Int,
    val contentVersion: String,
    val publishedAt: String,
)

@Serializable
data class CardsEnvelopeDto(
    val schemaVersion: Int,
    val cards: List<CardDto>,
)

@Serializable
data class CardDto(
    val id: String,
    val revision: Int,
    val status: String,
    val quote: String,
    val series: String,
    val volume: String,
    val workTitle: String,
    val authoredAt: String,
    val themes: List<String>,
    val interpretation: InterpretationDto,
    val historicalEvent: String,
    val background: String,
    val story: String,
    val imageId: String,
    val sources: List<SourceDto>,
    val reviewedAt: String,
)

@Serializable
data class InterpretationDto(
    val inspiration: String,
    val explanation: String,
)

@Serializable
data class SourceDto(
    val name: String,
    val url: String,
    val accessedAt: String,
    val type: String,
)

@Serializable
data class ImagesEnvelopeDto(
    val schemaVersion: Int,
    val images: List<ImageDto>,
)

@Serializable
data class ImageDto(
    val id: String,
    val localFile: String,
    val sha256: String,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val sourceUrl: String,
    val creator: String,
    val license: String,
    val licenseEvidence: String,
    val verifiedAt: String,
    val shareAllowed: Boolean,
)

@Serializable
data class WithdrawalsEnvelopeDto(
    val schemaVersion: Int,
    val withdrawals: List<WithdrawalDto>,
)

@Serializable
data class WithdrawalDto(
    val id: String,
    val revision: Int,
    val withdrawnAt: String,
)

@Serializable
data class RemoteManifestDto(
    val schemaVersion: Int,
    val contentVersion: String,
    val publishedAt: String,
    val minimumAppVersionCode: Int,
    val packageUrl: String,
    val packageBytes: Long,
    val packageSha256: String,
    val changes: ChangeSummaryDto,
    val releaseNotes: String,
)

@Serializable
data class ChangeSummaryDto(
    val added: Int,
    val updated: Int,
    val withdrawn: Int,
)

data class ParsedContentPackage(
    val info: PackageInfoDto,
    val cards: List<CardDto>,
    val images: List<ImageDto>,
    val withdrawals: List<WithdrawalDto>,
    val assets: Map<String, ByteArray>,
)
