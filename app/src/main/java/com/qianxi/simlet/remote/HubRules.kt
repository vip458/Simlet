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

    /**
     * 这条上报该不该发。
     *
     * 三道闸按「最可能挡住、也最该先答应的」顺序排：本机开关（用户随时能关）、后台熔断、时间间隔。
     * 顺序不是洁癖——本机开关关掉时连时间戳都不该读，这样「关掉统计」在代码路径上是可证的：
     * 关掉以后没有任何一条路径会走到 POST。
     */
    fun reportWanted(
        statsOn: Boolean,
        policyOn: Boolean,
        lastReportMs: Long,
        nowMs: Long,
        reportHours: Int
    ): Boolean {
        if (!statsOn || !policyOn) return false
        return shouldFetch(lastReportMs, nowMs, reportHours)
    }

    /**
     * 这台设备要不要提示更新。
     *
     * 顺序是有讲究的：先看有没有更新的版本，再看版本区间，最后看灰度。
     * 区间的语义更硬（「这个包不是给你这个版本准备的」），灰度只是投放节奏。
     */
    fun updateWanted(mineCode: Int, release: HubRelease?, seed: String): Boolean {
        if (release == null || release.code <= 0) return false
        if (mineCode >= release.code) return false
        if (!inRange(mineCode, release.minCode, release.maxCode)) return false
        return inRollout(seed, release.salt, release.rollout)
    }

    /** 区间两端都用 0 表示不限。填反了（上限比下限小）在服务端就被挡了，这里不再兜。 */
    fun inRange(code: Int, minCode: Int, maxCode: Int): Boolean {
        if (minCode > 0 && code < minCode) return false
        return !(maxCode > 0 && code > maxCode)
    }

    /**
     * 灰度命中判定。
     *
     * 桶号只由「本机种子 + 服务端盐」决定，所以同一台设备在同一批投放里结果稳定；
     * 后台换一个盐就能把同一个版本号重新投给另一批设备，不用动 versionCode。
     *
     * 用本机随机种子而不是匿名设备 ID 分桶，是为了「关掉统计之后灰度照样准」——
     * 设备 ID 一关就没法判，那等于让隐私开关顺带改变了功能可见性。
     */
    fun inRollout(seed: String, salt: String, percent: Int): Boolean {
        if (percent >= 100) return true
        if (percent <= 0) return false
        return bucketOf(seed, salt) < percent
    }

    /** 0~99。crc32 在 Android 和 JVM 上是同一份 java.util.zip 实现，跨版本稳定。 */
    fun bucketOf(seed: String, salt: String): Int {
        val crc = java.util.zip.CRC32()
        crc.update((seed + ":" + salt).toByteArray(Charsets.UTF_8))
        return (crc.value % 100L).toInt()
    }
}
