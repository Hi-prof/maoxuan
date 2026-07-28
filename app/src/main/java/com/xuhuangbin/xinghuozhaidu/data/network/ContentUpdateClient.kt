package com.xuhuangbin.xinghuozhaidu.data.network

import com.xuhuangbin.xinghuozhaidu.data.content.RemoteManifestDto
import com.xuhuangbin.xinghuozhaidu.data.content.ContentVersion
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.coroutines.coroutineContext

class ContentUpdateException(message: String) : IOException(message)

class ContentUpdateClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(12))
        .readTimeout(java.time.Duration.ofSeconds(40))
        .callTimeout(java.time.Duration.ofSeconds(90))
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    },
) {
    suspend fun fetchManifest(url: String): RemoteManifestDto = withContext(Dispatchers.IO) {
        requireAllowedUrl(url)
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ContentUpdateException("内容源返回 HTTP ${response.code}")
            }
            requireAllowedUrl(response.request.url.toString())
            val body = response.body ?: throw ContentUpdateException("内容源响应为空")
            val length = body.contentLength()
            if (length > MAX_MANIFEST_BYTES) throw ContentUpdateException("版本清单过大")
            val bytes = body.bytes()
            if (bytes.size > MAX_MANIFEST_BYTES) throw ContentUpdateException("版本清单过大")
            val manifest = try {
                json.decodeFromString<RemoteManifestDto>(bytes.decodeToString())
            } catch (error: Exception) {
                throw ContentUpdateException("版本清单格式无效：${error.message}")
            }
            validateManifest(manifest)
            manifest
        }
    }

    suspend fun downloadPackage(
        manifest: RemoteManifestDto,
        onProgress: (Float) -> Unit,
    ): ByteArray = withContext(Dispatchers.IO) {
        validateManifest(manifest)
        val request = Request.Builder().url(manifest.packageUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ContentUpdateException("内容包返回 HTTP ${response.code}")
            }
            requireAllowedUrl(response.request.url.toString())
            val body = response.body ?: throw ContentUpdateException("内容包响应为空")
            val declaredLength = body.contentLength()
            if (declaredLength > MAX_PACKAGE_BYTES || manifest.packageBytes > MAX_PACKAGE_BYTES) {
                throw ContentUpdateException("内容包超过 50 MiB 限制")
            }
            val output = ByteArrayOutputStream(manifest.packageBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            body.byteStream().use { input ->
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_PACKAGE_BYTES) throw ContentUpdateException("内容包超过 50 MiB 限制")
                    output.write(buffer, 0, read)
                    onProgress((total.toFloat() / manifest.packageBytes.coerceAtLeast(1)).coerceIn(0f, 1f))
                }
            }
            if (total != manifest.packageBytes) {
                throw ContentUpdateException("内容包大小不匹配")
            }
            val bytes = output.toByteArray()
            if (!bytes.sha256().equals(manifest.packageSha256, ignoreCase = true)) {
                throw ContentUpdateException("内容包 SHA-256 校验失败")
            }
            bytes
        }
    }

    private fun validateManifest(manifest: RemoteManifestDto) {
        if (manifest.schemaVersion != 1) throw ContentUpdateException("不支持的版本清单 schema")
        try {
            ContentVersion.requireValid(manifest.contentVersion)
            Instant.parse(manifest.publishedAt)
        } catch (error: Exception) {
            throw ContentUpdateException("版本清单中的版本或发布日期无效：${error.message}")
        }
        if (manifest.minimumAppVersionCode < 1) throw ContentUpdateException("最低 App 版本无效")
        if (manifest.packageBytes !in 1..MAX_PACKAGE_BYTES) throw ContentUpdateException("内容包大小无效")
        if (!SHA_256.matches(manifest.packageSha256)) throw ContentUpdateException("内容包哈希格式无效")
        if (manifest.changes.added < 0 || manifest.changes.updated < 0 || manifest.changes.withdrawn < 0) {
            throw ContentUpdateException("内容变更数量无效")
        }
        if (manifest.releaseNotes.isBlank()) throw ContentUpdateException("更新说明不能为空")
        requireAllowedUrl(manifest.packageUrl)
    }

    private fun requireAllowedUrl(value: String) {
        val url = value.toHttpUrlOrNull() ?: throw ContentUpdateException("内容地址无效")
        if (!url.isHttps) throw ContentUpdateException("内容地址必须使用 HTTPS")
        requireAllowedHost(url.host)
    }

    private fun requireAllowedHost(host: String) {
        if (host !in ALLOWED_HOSTS && !host.endsWith(".githubusercontent.com")) {
            throw ContentUpdateException("不允许的内容主机：$host")
        }
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val MAX_MANIFEST_BYTES = 256 * 1024
        const val MAX_PACKAGE_BYTES = 50L * 1024 * 1024
        val SHA_256 = Regex("^[0-9a-fA-F]{64}$")
        val ALLOWED_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "github-releases.githubusercontent.com",
        )
    }
}
