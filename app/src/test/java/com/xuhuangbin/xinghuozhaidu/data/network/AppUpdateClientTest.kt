package com.xuhuangbin.xinghuozhaidu.data.network

import com.xuhuangbin.xinghuozhaidu.domain.model.AppRelease
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppUpdateClientTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun findsHighestCompleteStableAppRelease() = runTest {
        val validVersion = "1.7.0"
        server.enqueue(
            MockResponse().setBody(
                releaseListJson(
                    releaseJson("content-v2.0.0", "2.0.0", draft = false, prerelease = false),
                    releaseJson("app-v2.0.0", "2.0.0", draft = true, prerelease = false),
                    releaseJson("app-v1.9.0", "1.9.0", draft = false, prerelease = true),
                    releaseJson("app-v1.8.0", "1.8.0", includeChecksum = false),
                    releaseJson("app-v$validVersion", validVersion),
                ),
            ),
        )
        val client = testClient()

        val update = client.findUpdate(server.url("/releases").toString(), "1.6.1")

        requireNotNull(update)
        assertEquals(validVersion, update.versionName)
        assertEquals("xinghuo-zhaidu-v1.7.0.apk", update.apkName)
        assertEquals(12_345L, update.apkBytes)
        assertEquals("修复与改进 1.7.0", update.releaseNotes)
    }

    @Test
    fun returnsNullWhenCurrentAppIsNewest() = runTest {
        server.enqueue(
            MockResponse().setBody(
                releaseListJson(releaseJson("app-v1.6.1", "1.6.1")),
            ),
        )

        val update = testClient().findUpdate(server.url("/releases").toString(), "1.6.1")

        assertNull(update)
    }

    @Test
    fun rejectsInsecureReleaseApiEvenWhenHostIsAllowed() = runTest {
        val client = AppUpdateClient(
            client = OkHttpClient(),
            allowedHosts = setOf(server.url("/").host),
        )

        val error = assertSuspendThrows<AppUpdateException> {
            client.findUpdate(server.url("/releases").toString(), "1.6.1")
        }

        assertTrue(error.message.orEmpty().contains("HTTPS"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun downloadsApkAfterMatchingPublishedChecksum() = runTest {
        val apkBytes = "signed-apk-payload".encodeToByteArray()
        val hash = apkBytes.sha256()
        val release = release(apkBytes.size.toLong())
        server.enqueue(MockResponse().setBody("$hash  ${release.apkName}\n"))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(apkBytes)))
        val destination = File(temporaryFolder.root, release.apkName)
        val progress = mutableListOf<Float>()

        val downloaded = testClient().download(release, destination, progress::add)

        assertEquals(destination.canonicalFile, downloaded.canonicalFile)
        assertTrue(destination.readBytes().contentEquals(apkBytes))
        assertEquals(1f, progress.last(), 0f)
        assertFalse(File(temporaryFolder.root, "${release.apkName}.part").exists())
    }

    @Test
    fun deletesCandidateWhenChecksumDoesNotMatch() = runTest {
        val apkBytes = "tampered-apk".encodeToByteArray()
        val release = release(apkBytes.size.toLong())
        server.enqueue(MockResponse().setBody("${ByteArray(32).sha256()}  ${release.apkName}\n"))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(apkBytes)))
        val destination = File(temporaryFolder.root, release.apkName)

        val error = assertSuspendThrows<AppUpdateException> {
            testClient().download(release, destination) {}
        }

        assertTrue(error.message.orEmpty().contains("SHA-256"))
        assertFalse(destination.exists())
        assertFalse(File(temporaryFolder.root, "${release.apkName}.part").exists())
    }

    @Test
    fun rejectsDownloadedSizeDifferentFromReleaseAsset() = runTest {
        val apkBytes = "short".encodeToByteArray()
        val release = release(apkBytes.size.toLong() + 1)
        server.enqueue(MockResponse().setBody("${apkBytes.sha256()}  ${release.apkName}\n"))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(apkBytes)))
        val destination = File(temporaryFolder.root, release.apkName)

        val error = assertSuspendThrows<AppUpdateException> {
            testClient().download(release, destination) {}
        }

        assertTrue(error.message.orEmpty().contains("大小"))
        assertFalse(destination.exists())
    }

    @Test
    fun rejectsApkLargerThanSafetyLimitBeforeDownload() = runTest {
        val release = release(151L * 1024 * 1024)
        val destination = File(temporaryFolder.root, release.apkName)

        val error = assertSuspendThrows<AppUpdateException> {
            testClient().download(release, destination) {}
        }

        assertTrue(error.message.orEmpty().contains("150 MiB"))
        assertEquals(0, server.requestCount)
        assertFalse(destination.exists())
    }

    @Test
    fun cancellationDeletesPartialApk() = runBlocking {
        val apkBytes = ByteArray(32 * 1024) { index -> index.toByte() }
        val release = release(apkBytes.size.toLong())
        server.enqueue(MockResponse().setBody("${apkBytes.sha256()}  ${release.apkName}\n"))
        server.enqueue(
            MockResponse()
                .setBody(okio.Buffer().write(apkBytes))
                .throttleBody(1024, 200, TimeUnit.MILLISECONDS),
        )
        val destination = File(temporaryFolder.root, release.apkName)
        val downloadStarted = CompletableDeferred<Unit>()

        val job = launch {
            testClient().download(release, destination) { progress ->
                if (progress > 0f) downloadStarted.complete(Unit)
            }
        }
        withTimeout(3_000) { downloadStarted.await() }
        job.cancelAndJoin()

        assertFalse(destination.exists())
        assertFalse(File(temporaryFolder.root, "${release.apkName}.part").exists())
    }

    private fun testClient() = AppUpdateClient(
        client = OkHttpClient(),
        allowedHosts = setOf(server.url("/").host),
        allowInsecureConnections = true,
    )

    private fun release(apkBytes: Long): AppRelease {
        val version = "1.7.0"
        return AppRelease(
            versionName = version,
            publishedAt = "2026-08-02T06:00:00Z",
            releaseNotes = "修复与改进",
            apkName = "xinghuo-zhaidu-v$version.apk",
            apkUrl = server.url("/app.apk").toString(),
            checksumUrl = server.url("/app.apk.sha256").toString(),
            apkBytes = apkBytes,
        )
    }

    private fun releaseListJson(vararg releases: String): String = releases.joinToString(
        prefix = "[",
        postfix = "]",
        separator = ",",
    )

    private fun releaseJson(
        tag: String,
        version: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        includeChecksum: Boolean = true,
    ): String {
        val apkName = "xinghuo-zhaidu-v$version.apk"
        val assets = buildList {
            add(
                """{"name":"$apkName","browser_download_url":"${server.url("/$apkName")}","size":12345}""",
            )
            if (includeChecksum) {
                add(
                    """{"name":"$apkName.sha256","browser_download_url":"${server.url("/$apkName.sha256")}","size":96}""",
                )
            }
        }.joinToString(",")
        return """
            {
              "tag_name":"$tag",
              "draft":$draft,
              "prerelease":$prerelease,
              "published_at":"2026-08-02T06:00:00Z",
              "body":"修复与改进 $version",
              "assets":[$assets],
              "ignored_future_field":true
            }
        """.trimIndent()
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(
        crossinline block: suspend () -> Unit,
    ): T = try {
        block()
        throw AssertionError("Expected ${T::class.java.simpleName}")
    } catch (error: Throwable) {
        if (error !is T) throw error
        error
    }
}
