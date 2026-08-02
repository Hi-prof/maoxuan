package com.xuhuangbin.xinghuozhaidu.domain.model

data class AppRelease(
    val versionName: String,
    val publishedAt: String,
    val releaseNotes: String,
    val apkName: String,
    val apkUrl: String,
    val checksumUrl: String,
    val apkBytes: Long,
)
