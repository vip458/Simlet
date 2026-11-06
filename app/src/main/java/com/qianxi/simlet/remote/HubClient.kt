package com.qianxi.simlet.remote

import android.util.Log
import com.qianxi.simlet.BuildConfig
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * 一条公告。
 *
 * `level` 只决定徽章，不驱动任何行为——工具类应用不该因为一条公告就拦操作或弹阻塞窗。
 *
 * [expMs] 是止期（epoch 毫秒），0 表示没有止期。服务端给的是秒，在这里一次换算掉，
 * 不让每个判据各乘一遍 1000。有了它，公告到点消失就不依赖网络：断网、后台宕了、
 * 被人关了熔断，本机自己就能把这条撤掉。
 *
 * [pubMs] 和 [live] 里，live 只有列表页用得上（bootstrap 那条通道只会给生效中的一条）。
 * 日期从 1.2.4 起两条通道都给，所以首页那一行、弹窗、列表行、通知栏说的是同一天，
 * 不必为它单独造一个类型。
 */
data class Notice(
    val id: Int,
    val title: String,
    val body: String,
    val url: String,
    val level: String,
    val expMs: Long = 0L,
    val pubMs: Long = 0L,
    val live: Boolean = true
) {
    /**
     * 这条公告是哪天发的。
     *
     * 当年只写月-日：一屏里全是「11-03」比全是「2026-11-03」好读，而年份是这一屏的共同前提；
     * 跨年才把年份带回来，否则翻到去年那条会读成「还没发生的 11-03」。
     * 后台没给 `at`（从没上线过的草稿不会下发，所以只剩「老包发的公告」这一种可能）时返回空串，
     * 界面上那一列整个不出现——不编一个「未知」占位。
     */
    fun dateText(): String {
        if (pubMs <= 0L) return ""
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = pubMs }
        val pattern = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) "MM-dd" else "yyyy-MM-dd"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(pubMs))
    }
}

/**
 * 采集策略。
 *
 * [revalidateMin] 是「进首页多久愿意再问一次公告」的分钟数，公告下线的时效由它决定；
 * [reportHours] 是上报间隔小时数；[statsOn] 决定后面那个上报端点要不要发。
 *
 * 服务端还有一个 `bm`（正文缓存上限，小时）这里不解析：它只用来给响应的 max-age 封顶，
 * 客户端读到的是 HTTP 头，不是这个字段。留一个读了不用的字段，下一个人就会去猜它该管什么。
 */
data class Policy(val revalidateMin: Int, val reportHours: Int, val statsOn: Boolean)

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
    val salt: String,
    /** 后台登记的包 sha256（`dg`）。空 = 没登记；非空时下载侧算完比对，对不上不装。 */
    val sha256: String = ""
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

/** 历史公告列表的三种结果，形状和 [HubOutcome] 一样，只是正文换成了列表。 */
sealed interface NoticesOutcome {

    data class Updated(val items: List<Notice>, val etag: String?) : NoticesOutcome

    /** 304：内存里那份列表还是最新的。 */
    data object Cached : NoticesOutcome

    /** 没拉到。列表页此时退回显示缓存里的那一条，而不是报错。 */
    data object Unreachable : NoticesOutcome
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
    private const val NOTICES_PATH = "/api/v1/notices.php"
    private const val TIMEOUT_MS = 8_000

    /** 后台硬上限是 1500 字节；读到 16KB 还没完就说明不对，直接放弃而不是撑大内存。 */
    private const val MAX_BYTES = 16 * 1024

    /** 历史列表的服务端上限是 24KB，留一倍余量；再大就不是后台发出来的东西。 */
    private const val NOTICES_MAX_BYTES = 64 * 1024

    /** 跳转次数上限，和 APK 下载侧同一个数。 */
    private const val MAX_HOPS = 5

    /** 只有这几个状态码是「换个地址再来一次」。304 不算——它就是答案本身，跟着走会把复验当成连不上。 */
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

    fun fetch(currentEtag: String?, lang: String, versionName: String, versionCode: Int): HubOutcome {
        val target = HubRules.url(BuildConfig.HUB_BASE_URL, PATH + query(lang, versionName, versionCode))
            ?: return HubOutcome.Unreachable
        val connection = openGet(target, currentEtag) ?: return HubOutcome.Unreachable
        val read = respond(connection, MAX_BYTES) ?: return HubOutcome.Unreachable
        if (read.notModified) {
            Log.d(TAG, "304，沿用本地缓存")
            return HubOutcome.Cached
        }
        val config = parseHubConfig(read.body)
        if (config == null) {
            Log.w(TAG, "正文解析失败（${read.body.length} 字节）")
            return HubOutcome.Unreachable
        }
        return HubOutcome.Updated(config, read.etag)
    }

    /**
     * 拉历史公告列表。
     *
     * 只在用户真的点进列表页时发，所以它不参与启动链路。带 ETag 复验：同一代次里翻第二次列表
     * 就是一个 304，只花一次握手，正文再不用重复下载。
     */
    fun fetchNotices(currentEtag: String?, lang: String): NoticesOutcome {
        val path = NOTICES_PATH + "?lang=" + URLEncoder.encode(lang, "UTF-8")
        val target = HubRules.url(BuildConfig.HUB_BASE_URL, path) ?: return NoticesOutcome.Unreachable
        val connection = openGet(target, currentEtag) ?: return NoticesOutcome.Unreachable
        val read = respond(connection, NOTICES_MAX_BYTES) ?: return NoticesOutcome.Unreachable
        if (read.notModified) return NoticesOutcome.Cached
        val items = parseNoticeList(read.body)
        if (items == null) {
            Log.w(TAG, "公告列表正文解析失败（${read.body.length} 字节）")
            return NoticesOutcome.Unreachable
        }
        return NoticesOutcome.Updated(items, read.etag)
    }

    /**
     * 两个 GET 共用这一条：超时、编码、ETag 头必须一致，抄两遍迟早会漏一条。
     *
     * 跳转自己跟、自己判，口径和更新包下载那条一致：每一跳都重新过 [HubRules.packageUrl]
     * （只认 https，本机回环例外）。放开自动跟随就等于放开「第一跳合规、第二跳随便」，
     * 而这条链路上返回什么 JSON 是对方说了算。返回的连接一定不是 3xx，跟不下去时是 null。
     */
    private fun openGet(target: URL, etag: String?): HttpURLConnection? {
        var current = target
        repeat(MAX_HOPS + 1) {
            val connection = connect(current, etag) ?: return null
            val code = runCatching { connection.responseCode }.getOrElse {
                connection.disconnect()
                Log.w(TAG, "读状态码失败: ${it.message}")
                return null
            }
            if (code !in REDIRECT_CODES) return connection
            val from = connection.url
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location.isNullOrBlank()) {
                Log.w(TAG, "HTTP $code 没给 Location")
                return null
            }
            val next = HubRules.packageUrl(URL(from, location).toString())
            if (next == null) {
                Log.w(TAG, "跳转目标不合规，不跟")
                return null
            }
            current = next
        }
        Log.w(TAG, "跳转超过 $MAX_HOPS 跳，放弃")
        return null
    }

    private fun connect(target: URL, etag: String?): HttpURLConnection? = runCatching {
        (target.openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("User-Agent", "Simlet-Android")
            // 空 ETag 不发这个头：带一个空值会让某些 CDN 直接跳过复验。
            etag?.takeIf { it.isNotBlank() }?.let { setRequestProperty("If-None-Match", it) }
        }
    }.getOrElse {
        Log.w(TAG, "打开连接失败: ${it.message}")
        null
    }

    /** 一次 GET 的结果：304、200 正文，或者 null（这次没问到）。 */
    private class ReadBody(val body: String, val notModified: Boolean, val etag: String?)

    /**
     * 读一次响应，非 2xx 和读失败都退化成 null。
     *
     * 上限按端点给：bootstrap 的正文硬上限是 1500 字节，16KB 已经是「不对」；
     * 列表端点最大 24KB，所以那里传 64KB。同一份读取代码，两个数。
     */
    private fun respond(connection: HttpURLConnection, limit: Int): ReadBody? = try {
        val code = connection.responseCode
        when {
            code == HttpURLConnection.HTTP_NOT_MODIFIED -> ReadBody("", true, connection.headerEtag())
            code in 200..299 -> ReadBody(readText(connection, limit), false, connection.headerEtag())
            else -> {
                Log.w(TAG, "HTTP $code")
                null
            }
        }
    } catch (error: Exception) {
        Log.w(TAG, "读取失败: ${error.message}")
        null
    } finally {
        connection.disconnect()
    }

    private fun HttpURLConnection.headerEtag(): String? =
        getHeaderField("ETag")?.takeIf { it.isNotBlank() }

    /** 解压前后各限一次：只限压缩后的话，一个 gzip 炸弹就能在 readBytes 里撑爆堆。 */
    private fun readText(connection: HttpURLConnection, limit: Int): String {
        val gzipped = connection.contentEncoding?.lowercase()?.contains("gzip") == true
        val raw = connection.inputStream.use { it.limitedBytes(limit) }
        if (!gzipped) return raw.toString(Charsets.UTF_8)
        val decoded = runCatching {
            GZIPInputStream(raw.inputStream()).use { it.limitedBytes(limit) }
        }.getOrElse {
            Log.w(TAG, "gzip 解压失败: ${it.message}")
            return ""
        }
        return decoded.toString(Charsets.UTF_8)
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
        val body = form(deviceId, brand, model, marketName(), sdk, versionName, versionCode, lang, event)
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

    /**
     * 厂商自己给的市场名，读不到就返回空串。
     *
     * 国产机的 Build.MODEL 是入网代号：Redmi Note 14 Pro 上报的是 24090RA29C，
     * 后台光看「品牌 + MODEL」根本认不出是哪台。名字在系统属性里，而两个属性名按厂商分头活着——
     * Xiaomi/HyperOS 给 ro.product.marketname（实测这台是 "Redmi Note 14 Pro"），
     * Honor/MagicOS 只给 ro.config.marketing_name（实测那台是 "荣耀X60 Pro"，前一个是空的）。
     *
     * SystemProperties 不是公开 API，所以整段包在 runCatching 里：读不到就退回旧口径。
     * 统计宁可名字糙，也不能因为一个属性读不出来就不发这条上报。
     */
    private fun marketName(): String = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val getter = clazz.getMethod("get", String::class.java)
        listOf("ro.product.marketname", "ro.product.odm.marketname", "ro.config.marketing_name")
            .mapNotNull { getter.invoke(null, it) as? String }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }.getOrDefault("")

    private fun form(
        deviceId: String,
        brand: String,
        model: String,
        market: String,
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
            // 老后台不认这个键会直接忽略，所以带上它是安全的。
            (if (market.isEmpty()) "" else "&n=" + enc(market.take(40))) +
            "&s=" + sdk +
            "&v=" + enc(versionName.take(20)) +
            "&c=" + versionCode +
            "&l=" + enc(lang) +
            "&e=" + enc(event)
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
        notice = json.optJSONObject("notice")?.let(::parseNotice)
            ?.takeIf { it.title.isNotBlank() },
        release = json.optJSONObject("rel")?.let { parseRelease(it) },
        policy = Policy(
            revalidateMin = policy?.optInt("bv", HubRules.DEFAULT_REVALIDATE_MIN) ?: HubRules.DEFAULT_REVALIDATE_MIN,
            reportHours = policy?.optInt("rp", 24) ?: 24,
            statsOn = policy?.optInt("on", if (HubRules.DEFAULT_STATS_ON) 1 else 0) != 0
        )
    )
}.getOrNull()

/** 公告的一条。bootstrap 和 notices 两个端点用的是同一个形状，所以解析也只写一处。 */
internal fun parseNotice(raw: JSONObject): Notice = Notice(
    // id 会被写进「打扰名额」的记账（RemoteStore.takeNoticeAttention 按最大值比较）。
    // 后台给一个 Int.MAX 的 id，之后所有真公告就永远不弹、不进通知栏，而界面上看不出任何原因。
    id = raw.optInt("id", 0).coerceIn(0, MAX_NOTICE_ID),
    title = raw.optString("ti"),
    body = raw.optString("bd"),
    url = raw.optString("ur"),
    level = raw.optString("lv", "info"),
    expMs = epochSecondsToMillis(raw.optLong("ex", 0L)),
    pubMs = epochSecondsToMillis(raw.optLong("at", 0L)),
    // 只有列表端点会带 on；没带就当生效中，因为 bootstrap 只会下发当前生效的那一条。
    live = raw.optInt("on", 1) != 0
)

/** 公告 id 的上限。后台自己递增到十亿之前都用不完，用完的是恶意或脏数据。 */
private const val MAX_NOTICE_ID = 1_000_000_000

/** 公元 2100 年，秒。再大的时间戳不是「很久以后」，是脏数据。 */
private const val MAX_EPOCH_SECONDS = 4_102_444_800L

/**
 * 秒转毫秒。`Long.MAX * 1000` 会绕成负数，那条公告于是变成「永久过期」——
 * 溢出在这里不是崩溃，是一个没人能看出来的错判定，所以在进模型之前先钳住。
 */
private fun epochSecondsToMillis(seconds: Long): Long =
    if (seconds <= 0L || seconds > MAX_EPOCH_SECONDS) 0L else seconds * 1000L

/**
 * 历史公告正文解析。整段包在 runCatching 里：一条脏数据不该让列表页白屏，
 * 退化成「没拉到」以后界面还能显示缓存里的那一条。
 */
internal fun parseNoticeList(body: String): List<Notice>? = runCatching {
    val array = JSONObject(body).optJSONArray("n") ?: return@runCatching emptyList()
    (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let(::parseNotice)?.takeIf { it.title.isNotBlank() }
    }
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
    salt = raw.optString("bk"),
    sha256 = HubRules.digest(raw.optString("dg"))
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
    .put("dg", sha256)
