package com.xuhuangbin.xinghuozhaidu.data.network

import com.xuhuangbin.xinghuozhaidu.data.content.ContentVersion
import com.xuhuangbin.xinghuozhaidu.domain.model.AppRelease
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.coroutineContext

class AppUpdateException(message: String) : IOException(message)

class AppUpdateClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(12))
        .readTimeout(Duration.ofSeconds(60))
        .callTimeout(Duration.ofMinutes(10))
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
    private val allowedHosts: Set<String> = DEFAULT_ALLOWED_HOSTS,
    private val allowInsecureConnections: Boolean = false,
) {
    suspend fun findUpdate(releasesUrl: String, currentVersion: String): AppRelease? =
        withContext(Dispatchers.IO) {
            requireVersion(currentVersion, "当前应用版本")
            val bytes = fetchBytes(releasesUrl, MAX_RELEASES_BYTES, "应用更新源")
            val releases = try {
                json.decodeFromString<List<GitHubReleaseDto>>(bytes.decodeToString())
            } catch (error: Exception) {
                throw AppUpdateException("应用更新清单格式无效：${error.message}")
            }
            releases.asSequence()
                .mapNotNull(::toAppRelease)
                .maxWithOrNull(Comparator { left, right ->
                    ContentVersion.compare(left.versionName, right.versionName)
                })
                ?.takeIf { release ->
                    ContentVersion.compare(release.versionName, currentVersion) > 0
                }
        }

    suspend fun download(
        release: AppRelease,
        destination: File,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        validateRelease(release)
        val parent = destination.parentFile
            ?: throw AppUpdateException("无法创建应用更新缓存")
        if ((!parent.exists() && !parent.mkdirs()) || !parent.isDirectory) {
            throw AppUpdateException("无法创建应用更新缓存")
        }
        val expectedDestinationName = release.apkName
        if (destination.name != expectedDestinationName) {
            throw AppUpdateException("应用安装包文件名无效")
        }
        val temporary = File(parent, "${destination.name}.part")
        destination.delete()
        temporary.delete()
        var promoted = false
        try {
            val expectedSha256 = fetchChecksum(release)
            val request = request(release.apkUrl)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AppUpdateException("应用安装包返回 HTTP ${response.code}")
                }
                requireAllowedUrl(response.request.url.toString())
                val body = response.body ?: throw AppUpdateException("应用安装包响应为空")
                val declaredBytes = body.contentLength()
                if (declaredBytes > MAX_APK_BYTES) {
                    throw AppUpdateException("应用安装包超过 150 MiB 限制")
                }
                if (declaredBytes >= 0 && declaredBytes != release.apkBytes) {
                    throw AppUpdateException("应用安装包大小与发布信息不匹配")
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var downloadedBytes = 0L
                temporary.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            downloadedBytes += read
                            if (downloadedBytes > MAX_APK_BYTES || downloadedBytes > release.apkBytes) {
                                throw AppUpdateException("应用安装包大小与发布信息不匹配")
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            onProgress(
                                (downloadedBytes.toFloat() / release.apkBytes)
                                    .coerceIn(0f, 1f),
                            )
                        }
                    }
                }
                if (downloadedBytes != release.apkBytes) {
                    throw AppUpdateException("应用安装包大小与发布信息不匹配")
                }
                val actualSha256 = digest.digest().toHex()
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    throw AppUpdateException("应用安装包 SHA-256 校验失败")
                }
            }
            promote(temporary, destination)
            promoted = true
            onProgress(1f)
            destination
        } finally {
            temporary.delete()
            if (!promoted) destination.delete()
        }
    }

    private fun toAppRelease(release: GitHubReleaseDto): AppRelease? {
        if (release.draft || release.prerelease) return null
        val version = APP_TAG.matchEntire(release.tagName)?.groupValues?.get(1) ?: return null
        if (!isValidVersion(version)) return null
        val publishedAt = release.publishedAt ?: return null
        try {
            Instant.parse(publishedAt)
        } catch (_: Exception) {
            return null
        }
        val apkName = "xinghuo-zhaidu-v$version.apk"
        val apk = release.assets.singleOrNull { it.name == apkName } ?: return null
        val checksum = release.assets.singleOrNull { it.name == "$apkName.sha256" } ?: return null
        if (apk.size !in 1..MAX_APK_BYTES || checksum.size !in 1..MAX_CHECKSUM_BYTES) return null
        return try {
            requireAllowedUrl(apk.downloadUrl)
            requireAllowedUrl(checksum.downloadUrl)
            AppRelease(
                versionName = version,
                publishedAt = publishedAt,
                releaseNotes = release.body.orEmpty().trim().ifBlank { "本次更新包含稳定性改进。" },
                apkName = apkName,
                apkUrl = apk.downloadUrl,
                checksumUrl = checksum.downloadUrl,
                apkBytes = apk.size,
            )
        } catch (_: AppUpdateException) {
            null
        }
    }

    private fun validateRelease(release: AppRelease) {
        requireVersion(release.versionName, "应用更新版本")
        val expectedName = "xinghuo-zhaidu-v${release.versionName}.apk"
        if (release.apkName != expectedName) throw AppUpdateException("应用安装包文件名无效")
        if (release.apkBytes !in 1..MAX_APK_BYTES) {
            throw AppUpdateException("应用安装包超过 150 MiB 限制")
        }
        requireAllowedUrl(release.apkUrl)
        requireAllowedUrl(release.checksumUrl)
    }

    private suspend fun fetchChecksum(release: AppRelease): String {
        val checksumBytes = fetchBytes(release.checksumUrl, MAX_CHECKSUM_BYTES, "安装包校验文件")
        val match = CHECKSUM_LINE.matchEntire(checksumBytes.decodeToString())
            ?: throw AppUpdateException("安装包校验文件格式无效")
        if (match.groupValues[2] != release.apkName) {
            throw AppUpdateException("安装包校验文件与目标 APK 不匹配")
        }
        return match.groupValues[1]
    }

    private suspend fun fetchBytes(url: String, maximumBytes: Long, label: String): ByteArray {
        val request = request(url)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw AppUpdateException("$label 返回 HTTP ${response.code}")
            requireAllowedUrl(response.request.url.toString())
            val body = response.body ?: throw AppUpdateException("$label 响应为空")
            val declaredBytes = body.contentLength()
            if (declaredBytes > maximumBytes) throw AppUpdateException("$label 过大")
            val output = ByteArrayOutputStream(
                declaredBytes.coerceIn(0, maximumBytes).toInt(),
            )
            var total = 0L
            body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maximumBytes) throw AppUpdateException("$label 过大")
                    output.write(buffer, 0, read)
                }
            }
            return output.toByteArray()
        }
    }

    private fun request(url: String): Request {
        requireAllowedUrl(url)
        return Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
    }

    private fun requireAllowedUrl(value: String) {
        val url = value.toHttpUrlOrNull() ?: throw AppUpdateException("应用更新地址无效")
        if (!url.isHttps && !allowInsecureConnections) {
            throw AppUpdateException("应用更新地址必须使用 HTTPS")
        }
        if (url.host !in allowedHosts && !url.host.endsWith(".githubusercontent.com")) {
            throw AppUpdateException("不允许的应用更新主机：${url.host}")
        }
    }

    private fun requireVersion(value: String, label: String) {
        if (!isValidVersion(value)) throw AppUpdateException("$label 格式无效：$value")
    }

    private fun isValidVersion(value: String): Boolean = try {
        ContentVersion.requireValid(value)
        true
    } catch (_: Exception) {
        false
    }

    private fun promote(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    @Serializable
    private data class GitHubReleaseDto(
        @SerialName("tag_name") val tagName: String,
        val draft: Boolean,
        val prerelease: Boolean,
        @SerialName("published_at") val publishedAt: String? = null,
        val body: String? = null,
        val assets: List<GitHubAssetDto> = emptyList(),
    )

    @Serializable
    private data class GitHubAssetDto(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
        val size: Long,
    )

    private companion object {
        const val MAX_RELEASES_BYTES = 1024L * 1024
        const val MAX_CHECKSUM_BYTES = 4L * 1024
        const val MAX_APK_BYTES = 150L * 1024 * 1024
        const val USER_AGENT = "XinghuoZhaidu-Android-AppUpdater"
        val APP_TAG = Regex("^app-v((?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*))$")
        val CHECKSUM_LINE = Regex("^([0-9a-fA-F]{64})\\s+\\*?([^\\r\\n]+)\\r?\\n?$")
        val DEFAULT_ALLOWED_HOSTS = setOf(
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "github-releases.githubusercontent.com",
        )
    }
}

private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
