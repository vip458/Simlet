package com.qianxi.simlet.remote

import java.net.URL

/**
 * 与自建后台有关的纯判定。不碰 Android API，也不碰网络，所以每条决定都能在 JVM 里钉住测试。
 *
 * 这里只出现契约里的字段名和口径。服务端加字段不影响本层；本层要用新字段，得先写进契约文档
 * （`docs/api-contract.md`，M5 落到开源仓库），不能让客户端悄悄依赖一个没写在文档上的行为。
 */
object HubRules {

    /** 服务端还没答过话时用的拉取间隔，和后台 `poll_boot_h` 的默认值一致。 */
    const val DEFAULT_BOOT_HOURS = 24

    /** 后台没答话时假定它允许上报，和后台 `poll_on` 的默认值一致；真拿到策略前不会真的上报。 */
    const val DEFAULT_STATS_ON = true

    /**
     * 地址合法性：只允许 https，唯一例外是本机回环。
     *
     * 放行回环是为了联调——手机上跑 `adb reverse` 打本机的 PHP 内置服务器，流量不出网卡，
     * 中间人无从站起。放开的范围就锁在这两个主机名上，不是「http 也行」。
     * 这样正式包里把前缀填成 http，得到的结果是这条链路静默关闭，而不是明文发出去。
     */
    fun endpoint(base: String): URL? {
        val trimmed = base.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val url = runCatching { URL(trimmed) }.getOrNull() ?: return null
        val host = url.host?.lowercase() ?: return null
        return when (url.protocol?.lowercase()) {
            "https" -> url
            "http" -> if (host == "127.0.0.1" || host == "localhost") url else null
            else -> null
        }
    }

    /** 联网特性是否开启。空串是构建时留下的开关，不是运行期错误。 */
    fun enabled(base: String): Boolean = endpoint(base) != null

    /**
     * 语言口径：只分中英，其它一律按中文取。
     *
     * 和后台 `Http::lang()` 是同一套规则——两边不一致的话，德语用户会在服务端落到 zh、
     * 在本地按 en 解释公告，出问题时谁都看不出来。
     */
    fun queryLang(tag: String?): String {
        val primary = tag?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')
        return if (primary == "en") "en" else "zh"
    }

    /**
     * 这一趟该不该发。
     *
     * 判定用的是「上次尝试」而不是「上次成功」：后台被墙或者正在维护时，
     * 每次启动都重试一遍只会让弱网用户多等一个连接超时，换不来任何结果。
     *
     * 一小时是 3_600_000 毫秒。写成 60_000 的话「每天一次」会变成「每 24 分钟一次」，
     * 月流量按 60 倍翻出去——这个单位换算是整套流量预算的地基，测试里专门钉一条。
     */
    fun shouldFetch(lastAttemptMs: Long, nowMs: Long, bootHours: Int): Boolean {
        if (lastAttemptMs <= 0L) return true
        return nowMs - lastAttemptMs >= hours(bootHours) * 3_600_000L
    }

    /** 间隔钳制：后台填错也不会出现「每秒钟问一次」或「永远不再问」。 */
    fun hours(value: Int): Long = value.coerceIn(1, 720).toLong()

    /**
     * 缓存的公告还算不算数。
     *
     * 200 和 304 都算「后台确认过」：304 的意思就是「你手上那份还是最新的」。
     * 只有长期一条都没确认过（后台换了、域名废了、这个 fork 指向了一个不存在的服务器）才不再显示——
     * 一条「v1.0.3 已发布」在用户已经装上 v1.0.3 半年之后还挂在首页，比没有公告更糟。
     */
    fun noticeLive(lastConfirmedMs: Long, nowMs: Long): Boolean {
        if (lastConfirmedMs <= 0L) return false
        return nowMs - lastConfirmedMs < CACHE_MS
    }

    /** 缓存有效期：比拉取间隔的上限（720 小时=30 天）还长就没意义了，取两者的同一个数。 */
    private const val CACHE_MS = 30 * 24 * 60 * 60 * 1000L
}
