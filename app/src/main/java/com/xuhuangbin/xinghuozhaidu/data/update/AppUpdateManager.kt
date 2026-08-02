package com.xuhuangbin.xinghuozhaidu.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.xuhuangbin.xinghuozhaidu.data.network.AppUpdateClient
import com.xuhuangbin.xinghuozhaidu.data.network.AppUpdateException
import com.xuhuangbin.xinghuozhaidu.domain.model.AppRelease
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppUpdateManager(
    context: Context,
    private val client: AppUpdateClient = AppUpdateClient(),
) {
    private val appContext = context.applicationContext
    private val updateDirectory = File(appContext.cacheDir, UPDATE_DIRECTORY)

    suspend fun findUpdate(releasesUrl: String, currentVersion: String): AppRelease? =
        client.findUpdate(releasesUrl, currentVersion)

    suspend fun download(
        release: AppRelease,
        onProgress: (Float) -> Unit,
    ): File {
        val destination = withContext(Dispatchers.IO) {
            if ((!updateDirectory.exists() && !updateDirectory.mkdirs()) || !updateDirectory.isDirectory) {
                throw AppUpdateException("无法创建应用更新缓存")
            }
            updateDirectory.listFiles()?.forEach { candidate ->
                if (candidate.name != release.apkName) candidate.delete()
            }
            File(updateDirectory, release.apkName)
        }
        return client.download(release, destination, onProgress)
    }

    fun canRequestPackageInstalls(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            appContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            throw AppUpdateException("无法打开安装权限设置")
        } catch (_: SecurityException) {
            throw AppUpdateException("无法打开安装权限设置")
        }
    }

    fun launchInstaller(apk: File) {
        val canonicalDirectory = canonicalFile(updateDirectory)
        val canonicalApk = canonicalFile(apk)
        if (canonicalApk.parentFile != canonicalDirectory ||
            canonicalApk.extension.lowercase() != "apk" ||
            !canonicalApk.isFile
        ) {
            throw AppUpdateException("应用安装包缓存无效，请重新下载")
        }
        if (!canRequestPackageInstalls()) {
            throw AppUpdateException("请先允许此应用安装未知应用")
        }
        val uri = try {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                canonicalApk,
            )
        } catch (_: IllegalArgumentException) {
            throw AppUpdateException("应用安装包缓存无效，请重新下载")
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (intent.resolveActivity(appContext.packageManager) == null) {
            throw AppUpdateException("系统中没有可用的应用安装器")
        }
        try {
            appContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            throw AppUpdateException("无法打开系统应用安装器")
        } catch (_: SecurityException) {
            throw AppUpdateException("无法打开系统应用安装器")
        }
    }

    private fun canonicalFile(file: File): File = try {
        file.canonicalFile
    } catch (_: IOException) {
        throw AppUpdateException("应用安装包缓存无效，请重新下载")
    } catch (_: SecurityException) {
        throw AppUpdateException("应用安装包缓存无效，请重新下载")
    }

    private companion object {
        const val UPDATE_DIRECTORY = "app-updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
