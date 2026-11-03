package com.qianxi.simlet.remote

import android.util.Log
import com.qianxi.simlet.BuildConfig
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/**
 * 一条公告。
 *
 * `level` 只决定徽章，不驱动任何行为——工具类应用不该因为一条公告就拦操作或弹阻塞窗。
 */
data class Notice(
    val id: Int,
    val title: String,
    val body: String,
    val url: String,
    val level: String
)

/** 采集策略。间隔按小时，statsOn 决定后面那个上报端点要不要发。 */
data class Policy(val bootHours: Int, val reportHours: Int, val statsOn: Boolean)

data class HubConfig(val notice: Notice?, val release: HubRelease?, val policy: Policy)

/**
 * 后台登记的版本。
 *
 * 判定新旧只看 [code]（versionCode），[name] 只是给人看的——GitHub 那条通道以前拿 versionName
 * 比较，两个同号不同 build 的包在它眼里是同一个版本，这里不再重复那个口径。
 *
 * [minCode]/[maxCode]/[rollout]/[salt] 是**下发到本地再判定**的触发条件：
 * 服务端按设备判定的话，响应内容就随设备变了，全局 ETag 当场失效，稳态流量翻几十倍。
 */
data class HubRelease(
    val name: String,
    val code: Int,
    val pageUrl: String,
    val apkUrl: String,
    val sizeBytes: Int,
    val mirrorLabel: String,
    val mirrorUrl: String,
    val mirrorCode: String,
    val notes: String,
    val critical: Boolean,
    val minCode: Int,
    val maxCode: Int,
    val rollout: Int,
    val salt: String
)

/**
 * 结果用 sealed interface 而不是异常表达，和 update/GitHubClient.kt 同一套约定：
 * 异常 message 会被当成详情文本上屏，那样就没法本地化。
 *
 * 这里更没有 Failed(reason)：公告拉取是后台动作，失败了什么都不显示，把原因送到界面上
 * 只会变成一句用户看不懂的「检查失败」。原因留在 logcat， TAG=SimletHub。
 */
sealed interface HubOutcome {

    /** 200，内容是新的。 */
    data class Updated(val config: HubConfig, val etag: String?) : HubOutcome

    /** 304：本地缓存就是最新的那份，什么都不用改。 */
    data object Cached : HubOutcome

    /** 不通、非 2xx、解析失败——一律按「这次没问到」处理。 */
    data object Unreachable : HubOutcome
}

/**
 * 读自建后台的公告与采集策略。
 *
 * 用 HttpURLConnection + 框架自带的 org.json，不为此加依赖；模板照 update/GitHubClient.kt。
 * 与 GitHub 那条通道唯一的区别是这里带 `If-None-Match`：这台服务器的响应内容只随后台改动变化，
 * 用 ETag 复验能把稳态流量压到几十字节，而月流量额度就是这个系统真正的天花板。
 */
object HubClient {

    private const val TAG = "SimletHub"
    private const val PATH = "/api/v1/bootstrap.php"
    private const val PING_PATH = "/api/v1/ping.php"
    private const val TIMEOUT_MS = 8_000

    /** 后台硬上限是 1500 字节；读到 16KB 还没完就说明不对，直接放弃而不是撑大内存。 */
    private const val MAX_BYTES = 16 * 1024

    fun fetch(currentEtag: String?, lang: String, versionName: String, versionCode: Int): HubOutcome {
        val target = HubRules.url(BuildConfig.HUB_BASE_URL, PATH + query(lang, versionName, versionCode))
            ?: return HubOutcome.Unreachable
        val connection = runCatching {
            (target.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("User-Agent", "Simlet-Android")
                // 空 ETag 不发这个头：带一个空值会让某些 CDN 直接跳过复验。
                currentEtag?.takeIf { it.isNotBlank() }?.let { setRequestProperty("If-None-Match", it) }
            }
        }.getOrElse {
            Log.w(TAG, "打开连接失败: ${it.message}")
            return HubOutcome.Unreachable
        }

        return try {
            val code = connection.responseCode
            when {
                code == HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    Log.d(TAG, "304，沿用本地缓存")
                    HubOutcome.Cached
                }

                code in 200..299 -> readBody(connection)
                else -> {
                    Log.w(TAG, "HTTP $code")
                    HubOutcome.Unreachable
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "读取失败: ${error.message}")
            HubOutcome.Unreachable
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 发一条匿名上报。返回是否发出去了。
     *
     * 服务端对这条路径永远回 204——校验失败、被限流、被熔断都是同一个响应，
     * 因为「哪种请求会被拒」本身是探测反馈。所以这里不解析正文，也不区分失败种类：
     * 区分了也没有可做的动作，重试一个「对方决定不收」的请求只会把流量额度花在空包上。
     *
     * 表单用短键名，和 docs/api-contract.md 一致；整套正文控制在几百字节，
     * 因为这条请求的固定开销是 TLS 握手，不是这几个字段。
     */
    fun report(
        deviceId: String,
        brand: String,
        model: String,
        sdk: Int,
        versionName: String,
        versionCode: Int,
        lang: String,
        event: String
    ): Boolean {
        val target = HubRules.url(BuildConfig.HUB_BASE_URL, PING_PATH) ?: return false
        val body = form(deviceId, brand, model, sdk, versionName, versionCode, lang, event)
        val connection = runCatching {
            (target.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("User-Agent", "Simlet-Android")
            }
        }.getOrElse {
            Log.w(TAG, "打不开上报通道: ${it.message}")
            return false
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            // 204 没有正文，但流还是要读干净：HttpURLConnection 只在连接被完整消费后才放回复用池。
            (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() }
            code in 200..299
        } catch (error: Exception) {
            Log.w(TAG, "上报没发出去: ${error.message}")
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun form(
        deviceId: String,
        brand: String,
        model: String,
        sdk: Int,
        versionName: String,
        versionCode: Int,
        lang: String,
        event: String
    ): String {
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        // 长度按服务端的裁剪上限来：本机就已经不会超，省得把一长串 Build.MODEL 的营销文案发过去。
        return "d=" + enc(deviceId) +
            "&m=" + enc(model.take(40)) +
            "&o=" + enc(brand.take(40)) +
            "&s=" + sdk +
            "&v=" + enc(versionName.take(20)) +
            "&c=" + versionCode +
            "&l=" + enc(lang) +
            "&e=" + enc(event)
    }

    private fun readBody(connection: HttpURLConnection): HubOutcome {
        val gzipped = connection.contentEncoding?.lowercase()?.contains("gzip") == true
        // 解压前后各限一次：后台的正常正文不到 1.5KB，超出上限一律按坏数据处理。
        // 只限压缩后的话，一个 gzip 炸弹就能在 readBytes 里撑爆堆。
        val raw = connection.inputStream.use { it.limitedBytes(MAX_BYTES) }
        val decoded = if (gzipped) {
            runCatching { GZIPInputStream(raw.inputStream()).use { it.limitedBytes(MAX_BYTES) } }
                .getOrElse {
                    Log.w(TAG, "gzip 解压失败: ${it.message}")
                    return HubOutcome.Unreachable
                }
        } else {
            raw
        }
        val config = parseHubConfig(decoded.toString(Charsets.UTF_8))
        if (config == null) {
            Log.w(TAG, "正文解析失败（${decoded.size} 字节）")
            return HubOutcome.Unreachable
        }
        return HubOutcome.Updated(config, connection.getHeaderField("ETag")?.takeIf { it.isNotBlank() })
    }

    /** 读到 limit 为止；超过就抛，不给对方用一个大响应换我们一次 OOM 的机会。 */
    private fun InputStream.limitedBytes(limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream(8 * 1024)
        val chunk = ByteArray(4 * 1024)
        var total = 0
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            total += read
            if (total > limit) throw java.io.IOException("响应超过 ${limit} 字节")
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private fun query(lang: String, versionName: String, versionCode: Int): String {
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        return "?lang=${enc(lang)}&vn=${enc(versionName)}&vc=$versionCode"
    }
}

/**
 * 把响应正文读成配置。
 *
 * 兼容红线在这里：字段只认得到的，缺的一律给默认值，多的直接忽略。后台将来加字段
 * 不该让这个已发出去的版本解析失败——所以整段解析包在 runCatching 里，坏数据退化成「没问到」。
 *
 * internal 是为了让 JVM 单测能直接喂文本；不对外暴露，避免有人把它当公共 API 依赖。
 */
internal fun parseHubConfig(body: String): HubConfig? = runCatching {
    val json = JSONObject(body)
    val policy = json.optJSONObject("pol")
    HubConfig(
        notice = json.optJSONObject("notice")?.let { raw ->
            Notice(
                id = raw.optInt("id", 0),
                title = raw.optString("ti"),
                body = raw.optString("bd"),
                url = raw.optString("ur"),
                level = raw.optString("lv", "info")
            )
        }?.takeIf { it.title.isNotBlank() },
        release = json.optJSONObject("rel")?.let { parseRelease(it) },
        policy = Policy(
            bootHours = policy?.optInt("bm", HubRules.DEFAULT_BOOT_HOURS) ?: HubRules.DEFAULT_BOOT_HOURS,
            reportHours = policy?.optInt("rp", 24) ?: 24,
            statsOn = policy?.optInt("on", if (HubRules.DEFAULT_STATS_ON) 1 else 0) != 0
        )
    )
}.getOrNull()

/** 后台的短键名与 docs/api-contract.md 一一对应；缺字段一律给「不影响判定」的默认值。 */
internal fun parseRelease(raw: JSONObject): HubRelease = HubRelease(
    name = raw.optString("nm"),
    code = raw.optInt("cd", 0),
    pageUrl = raw.optString("pg"),
    apkUrl = raw.optString("ap"),
    sizeBytes = raw.optInt("sz", 0),
    mirrorLabel = raw.optString("mn"),
    mirrorUrl = raw.optString("mu"),
    mirrorCode = raw.optString("mk"),
    notes = raw.optString("nt"),
    critical = raw.optInt("fc", 0) == 1,
    minCode = raw.optInt("mi", 0),
    maxCode = raw.optInt("ma", 0),
    rollout = raw.optInt("ro", 100),
    salt = raw.optString("bk")
)

/**
 * 缓存用的反向转换。
 *
 * 存的是短键名而不是 Kotlin 字段名：这样 prefs 里的那段文本和后台发来的正文是同一种东西，
 * 出问题时把两边对一眼就知道是解析错了还是存错了。
 */
internal fun HubRelease.toJson(): JSONObject = JSONObject()
    .put("nm", name)
    .put("cd", code)
    .put("pg", pageUrl)
    .put("ap", apkUrl)
    .put("sz", sizeBytes)
    .put("mn", mirrorLabel)
    .put("mu", mirrorUrl)
    .put("mk", mirrorCode)
    .put("nt", notes)
    .put("fc", if (critical) 1 else 0)
    .put("mi", minCode)
    .put("ma", maxCode)
    .put("ro", rollout)
    .put("bk", salt)
