package com.qianxi.simlet.update

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 一条 GitHub Release 里我们关心的字段。 */
data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?
)

/** 检查结果用枚举而不是异常表达：异常 message 会被当成详情文本上屏，那样就没法本地化。 */
sealed interface CheckOutcome {
    data class Available(val release: ReleaseInfo) : CheckOutcome
    data object UpToDate : CheckOutcome
    data object NoRelease : CheckOutcome
    data object RateLimited : CheckOutcome
    data class Failed(val reason: String) : CheckOutcome
}

/**
 * 异常 message 直接上屏，所以空串和 null 一样要兜底。
 *
 * 真机走查遇到过一次移动网络下抛出的异常 message 为空，界面上只剩「检查失败」却没有任何解释。
 */
internal fun Throwable.detailOr(fallback: String): String =
    message?.takeIf { it.isNotBlank() } ?: fallback

/**
 * 只读 GitHub 的公开 Releases 接口。
 *
 * 未认证时限 60 次/小时/IP，所以只在用户主动点「检查更新」时请求，不做后台轮询。
 * 用 HttpURLConnection + 框架自带的 org.json，不为此加依赖。
 */
object GitHubClient {

    private const val OWNER = "vip458"
    private const val REPO = "Simlet"
    private const val ENDPOINT = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val TIMEOUT_MS = 8_000

    fun fetchLatest(currentVersion: String): CheckOutcome {
        val connection = runCatching {
            (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "$REPO-Android")
            }
        }.getOrElse { return CheckOutcome.Failed(it.detailOr("connection")) }

        return try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }
                ?: return CheckOutcome.Failed("empty response")
            when {
                code == 404 -> CheckOutcome.NoRelease
                code == 403 || code == 429 -> CheckOutcome.RateLimited
                code !in 200..299 -> CheckOutcome.Failed("HTTP $code")
                else -> evaluate(body, currentVersion)
            }
        } catch (error: IOException) {
            CheckOutcome.Failed(error.detailOr("io"))
        } finally {
            connection.disconnect()
        }
    }

    private fun evaluate(body: String, currentVersion: String): CheckOutcome = runCatching {
        val json = JSONObject(body)
        val release = parse(json)
        when {
            compareVersions(release.versionName, currentVersion) > 0 -> CheckOutcome.Available(release)
            else -> CheckOutcome.UpToDate
        }
    }.getOrElse { CheckOutcome.Failed("bad payload: ${it.detailOr("parse")}") }

    private fun parse(json: JSONObject): ReleaseInfo {
        val tag = json.optString("tag_name")
        val assets = json.optJSONArray("assets") ?: JSONArray()
        val apk = (0 until assets.length())
            .asSequence()
            .map(assets::getJSONObject)
            .firstOrNull { it.optString("name").endsWith(".apk") }
            ?.optString("browser_download_url")
            ?.takeIf { it.isNotBlank() }
        return ReleaseInfo(
            tagName = tag,
            versionName = tag.removePrefix("v"),
            notes = json.optString("body"),
            pageUrl = json.optString("html_url"),
            apkUrl = apk
        )
    }

    /** 按点分数字段比较；预发布后缀（-beta 等）先截掉再比。 */
    internal fun compareVersions(left: String, right: String): Int {
        val a = left.trim().removePrefix("v").substringBefore('-').split('.')
        val b = right.trim().removePrefix("v").substringBefore('-').split('.')
        for (index in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrNull(index)?.trim()?.toIntOrNull() ?: 0
            val y = b.getOrNull(index)?.trim()?.toIntOrNull() ?: 0
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
