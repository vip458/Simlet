package com.qianxi.simlet.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.qianxi.simlet.remote.HubRules
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 下载升级包并拉起系统安装器。
 *
 * 包落在应用内部存储（filesDir）而不是外部私有目录：Android 10 及以下，持有传统存储权限的
 * 同机应用能读写 `/sdcard/Android/data/<包名>/files/…`，于是「下载完成 → 点安装」之间
 * 那几秒足够它把 simlet-latest.apk 换成别的东西。内部存储没有这条路。
 */
object ApkInstaller {

    private const val FILE_NAME = "simlet-latest.apk"

    /** 跳转次数上限。GitHub 的 release 资产一般一到两跳，给到五跳是余量。 */
    private const val MAX_HOPS = 5

    /** 后台没声明包大小时的硬上限：正常包 1.4MB 左右，20MB 已经不是这个项目的产物。 */
    private const val MAX_BYTES = 20L * 1024 * 1024

    private fun updatesDir(context: Context): File =
        File(context.filesDir, "updates").apply { mkdirs() }

    /**
     * 同步下载，调用方负责放到 IO 线程；进度回调 0–100，未知总长时不回调。
     *
     * @param declaredBytes 后台声明的包大小（`sz`），非正数时按 [MAX_BYTES] 兜底。
     * @param expectedSha256 后台登记的包摘要（`dg`）。留空就不做落地校验；非空时算出来
     *   对不上直接丢掉文件——装不上只是麻烦，装错包是把别人的代码写进这台手机。
     */
    fun download(
        context: Context,
        url: String,
        declaredBytes: Long,
        expectedSha256: String = "",
        onProgress: (Int) -> Unit
    ): Result<File> =
        runCatching {
            val limit = if (declaredBytes > 0L) declaredBytes else MAX_BYTES
            val target = File(updatesDir(context), FILE_NAME)
            // 先落到 .part 再改名：半途而废的文件不该被上一次的成功记录认成「已下载」。
            val temp = File(target.parentFile, FILE_NAME + ".part")
            var connection: HttpURLConnection? = null
            try {
                connection = openTrusted(url)
                val code = connection.responseCode
                if (code !in 200..299) error("HTTP $code")
                val declared = connection.contentLength.toLong().takeIf { it > 0 } ?: -1L
                if (declared > limit) error("size-declared-over-limit")
                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            written += read
                            // 声明值只说明对方「打算」发多少；真正保住磁盘的是这里逐块累加的实测值。
                            if (written > limit) error("size-over-limit")
                            output.write(buffer, 0, read)
                            if (declared > 0) onProgress(((written * 100) / declared).toInt())
                        }
                    }
                }
                if (expectedSha256.isNotBlank() && !digestMatches(temp, expectedSha256)) {
                    error("digest-mismatch")
                }
                if (!temp.renameTo(target)) error("rename-failed")
                target
            } finally {
                connection?.disconnect()
                runCatching { temp.delete() }
            }
        }

    /**
     * 实算文件的 sha256 和后台登记的值比一遍。
     *
     * 流式读，别整包进内存。期望值形状不对（不是 64 个十六进制）也算不匹配——那说明登记
     * 的东西本身不可信，宁可不装。
     */
    internal fun digestMatches(file: File, expected: String): Boolean {
        val want = expected.trim().lowercase()
        if (!HubRules.SHA256_PATTERN.matches(want)) {
            return false
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val got = digest.digest().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return got == want
    }

    /**
     * 按 [HubRules.packageUrl] 的口径打开连接，并自己跟随跳转。
     *
     * 自动跟随（instanceFollowRedirects）在这里不能用：跳转目标不再经过任何检查，
     * 一个合规的 https 第一跳就能把人带到明文或者别的 scheme 上去。
     */
    private fun openTrusted(url: String): HttpURLConnection {
        var current = HubRules.packageUrl(url) ?: error("url-rejected")
        repeat(MAX_HOPS) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                instanceFollowRedirects = false
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) error("redirect-no-location")
                current = HubRules.packageUrl(URL(current, location).toString()) ?: error("redirect-rejected")
            } else {
                return connection
            }
        }
        error("too-many-redirects")
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
