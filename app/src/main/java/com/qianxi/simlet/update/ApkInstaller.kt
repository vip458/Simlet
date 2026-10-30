package com.qianxi.simlet.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 下载升级包并拉起系统安装器。
 *
 * 包放在应用外部私有目录而不是 cacheDir：系统缓存清理可能在校验/安装之前把包删掉。
 */
object ApkInstaller {

    private const val FILE_NAME = "simlet-latest.apk"

    private fun updatesDir(context: Context): File =
        File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }

    /** 同步下载，调用方负责放到 IO 线程；进度回调 0–100，未知总长时不回调。 */
    fun download(context: Context, url: String, onProgress: (Int) -> Unit): Result<File> = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            val total = connection.contentLength.toLong().takeIf { it > 0 } ?: -1L
            val target = File(updatesDir(context), FILE_NAME)
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress(((written * 100) / total).toInt())
                    }
                }
            }
            target
        } finally {
            connection.disconnect()
        }
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Android 8 及以上的「安装未知应用」授权入口。 */
    fun permissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
