package com.qianxi.simlet.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 公告和更新这两条链路上会真的影响行为的判断都在这里钉住。
 *
 * 挑的不是「能跑」，而是三类出问题时用户看不见的地方：
 * 地址填错到底是静默关闭还是明文发出去、后台宕了会不会每次启动都白等一个超时、
 * 一条过期公告会不会一直挂着；以及最贵的一类——该提示的版本没提示、不该提示的设备被反复打扰。
 */
class HubRulesTest {

    private val hour = 60 * 60 * 1000L

    @Test
    fun `空地址等于整条链路关闭`() {
        assertNull(HubRules.endpoint(""))
        assertNull(HubRules.endpoint("   "))
        assertFalse(HubRules.enabled(""))
    }

    @Test
    fun `https 才允许，公网 http 一律拒绝`() {
        assertNotNull(HubRules.endpoint("https://hub.example.com"))
        assertNotNull(HubRules.endpoint("https://hub.example.com/simlet-api"))
        // 填错前缀的后果必须是「不发请求」，不是「把版本号和语言明文发出去」。
        assertNull(HubRules.endpoint("http://hub.example.com"))
        assertNull(HubRules.endpoint("ftp://hub.example.com"))
        assertNull(HubRules.endpoint("hub.example.com"))
        assertNull(HubRules.endpoint("这不是地址"))
    }

    @Test
    fun `本机回环放行 http，联调用`() {
        assertNotNull(HubRules.endpoint("http://127.0.0.1:8642"))
        assertNotNull(HubRules.endpoint("http://localhost:8642"))
        // 回环之外的 127 打头网段不算本机。
        assertNull(HubRules.endpoint("http://127.1.2.3:8642"))
    }

    @Test
    fun `末尾斜杠不影响拼接`() {
        assertEquals("hub.example.com", HubRules.endpoint("https://hub.example.com/simlet-api/")?.host)
        assertEquals("/simlet-api", HubRules.endpoint("https://hub.example.com/simlet-api/")?.path)
    }

    @Test
    fun `语言只分中英，其它按中文取`() {
        assertEquals("en", HubRules.queryLang("en"))
        assertEquals("en", HubRules.queryLang("en-US"))
        assertEquals("en", HubRules.queryLang("EN_gb"))
        assertEquals("zh", HubRules.queryLang("zh"))
        assertEquals("zh", HubRules.queryLang("de"))
        assertEquals("zh", HubRules.queryLang(null))
    }

    @Test
    fun `没问过就直接问，问过就按间隔`() {
        val now = 1_762_000_000_000L
        assertTrue(HubRules.shouldFetch(0L, now, 24))
        assertFalse(HubRules.shouldFetch(now - hour, now, 24))
        assertTrue(HubRules.shouldFetch(now - 25 * hour, now, 24))
        // 边界取等号：正好到点该问。
        assertTrue(HubRules.shouldFetch(now - 24 * hour, now, 24))
    }

    @Test
    fun `时间戳在未来时这次跳过，但不会永久停摆`() {
        // 用户改了系统时间、或从别的设备倒过数据时，上次尝试的时间戳可能比现在晚。
        // 这次不问没关系，关键是它必须会自己恢复——不能因为差值变成负数就再也不拉公告。
        val now = 1_762_000_000_000L
        assertFalse(HubRules.shouldFetch(now + 1000, now, 24))
        assertTrue(HubRules.shouldFetch(now + 1000, now + 25 * hour, 24))
    }

    @Test
    fun `间隔被钳在合理区间，后台填错也不会自旋`() {
        assertEquals(1L, HubRules.hours(0))
        assertEquals(1L, HubRules.hours(-5))
        assertEquals(720L, HubRules.hours(100000))
        assertEquals(24L, HubRules.hours(24))
        // 填 0 的实际后果：一小时一次。既不是每次启动都问，也不是永不问。
        val now = 1_762_000_000_000L
        assertFalse(HubRules.shouldFetch(now - 10 * 60_000L, now, 0))
        assertTrue(HubRules.shouldFetch(now - 2 * hour, now, 0))
    }

    @Test
    fun `公告缓存要后台确认过才算数，三十天后自动失效`() {
        val now = 1_762_000_000_000L
        val day = 24 * hour
        assertFalse(HubRules.noticeLive(0L, now))
        assertTrue(HubRules.noticeLive(now - day, now))
        assertTrue(HubRules.noticeLive(now - 29 * day, now))
        assertFalse(HubRules.noticeLive(now - 31 * day, now))
    }

    @Test
    fun `只有后台登记的版本比本机新才提示`() {
        assertFalse(HubRules.updateWanted(3, null, SEED))
        // cd=0 是「后台没填」，不是「有个 0 号的包」。
        assertFalse(HubRules.updateWanted(3, release(code = 0), SEED))
        assertFalse(HubRules.updateWanted(4, release(code = 4), SEED))
        // 本机比后台登记的还新（内测包、或后台登记的版本还没上线）：安静，不提示降级。
        assertFalse(HubRules.updateWanted(5, release(code = 4), SEED))
        assertTrue(HubRules.updateWanted(3, release(code = 4), SEED))
    }

    @Test
    fun `版本区间两端都留空等于不限`() {
        assertTrue(HubRules.inRange(3, 0, 0))
        assertTrue(HubRules.inRange(3, 3, 0))
        assertFalse(HubRules.inRange(2, 3, 0))
        assertTrue(HubRules.inRange(3, 0, 3))
        assertFalse(HubRules.inRange(4, 0, 3))
        assertTrue(HubRules.inRange(3, 2, 4))
        // 区间是闭区间：填 mi=3、ma=3 就是只投 3 这一个版本。
        assertFalse(HubRules.updateWanted(3, release(code = 4, minCode = 4), SEED))
        assertFalse(HubRules.updateWanted(3, release(code = 4, maxCode = 2), SEED))
    }

    @Test
    fun `灰度百分之百全命中，零全不命中`() {
        assertTrue(HubRules.inRollout(SEED, "v4", 100))
        assertFalse(HubRules.inRollout(SEED, "v4", 0))
        assertFalse(HubRules.inRollout(SEED, "v4", -1))
        assertTrue(HubRules.inRollout(SEED, "v4", 101))
        // 中间的档位就是「桶号 < 百分比」：判定和桶号在同一处定义，不会出现两套口径。
        val bucket = HubRules.bucketOf(SEED, "v4")
        assertFalse(HubRules.inRollout(SEED, "v4", bucket))
        assertTrue(HubRules.inRollout(SEED, "v4", bucket + 1))
    }

    @Test
    fun `桶号稳定且落在 0 到 99`() {
        assertEquals(HubRules.bucketOf(SEED, "v4"), HubRules.bucketOf(SEED, "v4"))
        for (salt in listOf("v4", "r4", "v10", "8k2d")) {
            val bucket = HubRules.bucketOf(SEED, salt)
            assertTrue("桶号越界：$bucket", bucket in 0..99)
        }
        // 换桶盐要真的换一批设备：同一台机器在两个盐下多数情况会落到不同桶，
        // 否则「把同一个版本重投另一批」这个功能就是空的。
        val buckets = (0 until 50).map { HubRules.bucketOf("seed$it", "v4") to HubRules.bucketOf("seed$it", "r4") }
        assertTrue("换盐后桶号完全没变", buckets.any { it.first != it.second })
    }

    @Test
    fun `灰度按百分比放量，比例落在同一量级`() {
        // 35% 的投放不该出现「一个人都不中」或「全都中」。200 台设备是个折衷：
        // 二项分布的标准差约 6.7，取 ±2σ 的界，既能钉住实现走样，又不会让测试随机翻红。
        val hit = (0 until 200).count { HubRules.inRollout("seed$it", "v4", 35) }
        assertTrue("命中数异常：$hit", hit in 21..119)
    }

    @Test
    fun `投放范围外的设备不提示，范围内的照常`() {
        // 后台一次投放同时带区间和灰度：区间外的 3 号设备安静，区间内的 3 号设备命中。
        assertTrue(HubRules.updateWanted(3, release(code = 4, minCode = 3, rollout = 100), SEED))
        assertFalse(HubRules.updateWanted(3, release(code = 4, minCode = 3, rollout = 0), SEED))
        assertTrue(HubRules.updateWanted(3, release(code = 4, rollout = 100, critical = true), SEED))
    }

    @Test
    fun `本机开关或后台熔断任一关掉就不上报`() {
        val now = 1_762_000_000_000L
        // 两个关掉都要挡住，而且挡在时间判断之前：「关掉统计」必须在这条链路上没有出口，
        // 不能只是「今天先不发」，也不能关掉以后还去读一遍时间戳算该不该发。
        assertFalse(HubRules.reportWanted(false, true, 0L, now, 24))
        assertFalse(HubRules.reportWanted(true, false, 0L, now, 24))
        assertFalse(HubRules.reportWanted(false, false, 0L, now, 24))
        assertTrue(HubRules.reportWanted(true, true, 0L, now, 24))
    }

    @Test
    fun `上报间隔独立于配置拉取间隔`() {
        val now = 1_762_000_000_000L
        // 后台把 rp 调到 72 小时（runbook 降流量的第 1 步）时，昨天报过的今天不该再报。
        assertFalse(HubRules.reportWanted(true, true, now - 25 * hour, now, 72))
        assertTrue(HubRules.reportWanted(true, true, now - 73 * hour, now, 72))
        // 和 GET 用的是同一套间隔语义，所以 0 和超界值同样被钳在 1~720 小时。
        assertFalse(HubRules.reportWanted(true, true, now - 10 * 60_000L, now, 0))
        assertTrue(HubRules.reportWanted(true, true, now - 2 * hour, now, 0))
    }

    private fun release(
        code: Int = 4,
        minCode: Int = 0,
        maxCode: Int = 0,
        rollout: Int = 100,
        salt: String = "v4",
        critical: Boolean = false
    ) = HubRelease(
        name = "1.0.3",
        code = code,
        pageUrl = "https://github.com/vip458/Simlet/releases/tag/v1.0.3",
        apkUrl = "https://github.com/vip458/Simlet/releases/download/v1.0.3/app-release.apk",
        sizeBytes = 1438720,
        mirrorLabel = "夸克网盘",
        mirrorUrl = "https://pan.quark.cn/s/000000000000",
        mirrorCode = "提取码 8k2d",
        notes = "1. 修复无法识别sim卡问题",
        critical = critical,
        minCode = minCode,
        maxCode = maxCode,
        rollout = rollout,
        salt = salt
    )

    private companion object {
        /** 固定种子：分桶测试结果必须可复现，所以这里不能用随机种子。 */
        const val SEED = "0f1e2d3c4b5a69788796a5b4c3d2e1f0"
    }
}
