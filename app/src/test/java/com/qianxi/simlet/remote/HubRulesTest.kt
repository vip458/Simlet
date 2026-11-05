package com.qianxi.simlet.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun `缓存归属认到目录一级，换后台地址就不是同一个源站`() {
        // ETag 是源站给自己的标识，跨源复用等于拿 A 的凭据去问 B：B 只要算出同样的串就会回 304，
        // 客户端于是把 A 的旧公告一直当成 B 的最新内容。所以归属键必须精确到目录。
        assertEquals("https://hub.example.com/simlet-api", HubRules.origin("https://hub.example.com/simlet-api/"))
        assertNotEquals(
            HubRules.origin("https://hub.example.com/simlet-api"),
            HubRules.origin("https://hub.example.com/other-api")
        )
        assertNotEquals(
            HubRules.origin("https://a.example/hub"),
            HubRules.origin("https://b.example/hub")
        )
        // 公网 http 连链路都是关闭的，归属键也就没有值，不能退化成空串去匹配上一次的缓存。
        assertNull(HubRules.origin("http://hub.example.com"))
        assertNull(HubRules.origin(""))
    }

    @Test
    fun `后台装在子目录里时端点要接在目录后面`() {
        // URL(base, "/api/v1/x") 会把 base 的目录丢掉，这条是虚拟主机唯一的可用形态。
        assertEquals(
            "https://h.example/simlet-hub/api/v1/bootstrap.php",
            HubRules.url("https://h.example/simlet-hub", "/api/v1/bootstrap.php")?.toString()
        )
        assertEquals(
            "https://h.example/simlet-api/api/v1/ping.php",
            HubRules.url("https://h.example/simlet-api/", "/api/v1/ping.php")?.toString()
        )
        assertEquals(
            "https://h.example/api/v1/bootstrap.php",
            HubRules.url("https://h.example", "/api/v1/bootstrap.php")?.toString()
        )
        assertNull(HubRules.url("", "/api/v1/bootstrap.php"))
        assertNull(HubRules.url("http://h.example/hub", "/api/v1/bootstrap.php"))
    }

    @Test
    fun `下载地址只收 https，公网 http 与别的 scheme 一律不下`() {
        assertNotNull(HubRules.packageUrl("https://github.com/vip458/Simlet/releases/download/v1.0.3/app-release.apk"))
        // 明文下一个 APK：链路中间多一个 MITM，换出来的包直接进系统安装器。
        assertNull(HubRules.packageUrl("http://github.com/vip458/Simlet/app-release.apk"))
        // 后台被改掉以后这串字符不再是「一个网页地址」，而是「读哪个文件」。
        assertNull(HubRules.packageUrl("file:///sdcard/Download/evil.apk"))
        assertNull(HubRules.packageUrl("content://media/external.apk"))
        assertNull(HubRules.packageUrl("jar:https://x/y!app.apk"))
        assertNull(HubRules.packageUrl(""))
        assertNull(HubRules.packageUrl("   "))
        assertNull(HubRules.packageUrl("不是地址"))
        // 联调例外和 endpoint() 同一套：adb reverse 与模拟器宿主机都不出网卡。
        assertNotNull(HubRules.packageUrl("http://127.0.0.1:8642/app.apk"))
        assertNotNull(HubRules.packageUrl("http://10.0.2.2:8642/app.apk"))
        assertNull(HubRules.packageUrl("http://127.1.2.3:8642/app.apk"))
    }

    @Test
    fun `给人看的链接允许 http，但只允许网页`() {
        // 网盘镜像常年是 http，这一条锁死 https 会让「打开镜像」变成静默无响应。
        assertEquals("http://pan.example/s/abc", HubRules.webUrl("http://pan.example/s/abc"))
        assertEquals("https://pan.example/s/abc", HubRules.webUrl("https://pan.example/s/abc"))
        // intent:// 交给 ACTION_VIEW 会去拉起别的组件，tel:/sms: 会拨号或发短信，
        // file:// 能把本机任意路径交给能处理该 MIME 的应用。这三种都不是「打开一个网页」。
        assertNull(HubRules.webUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(HubRules.webUrl("file:///data/data/com.qianxi.simlet/shared_prefs/simlet_remote.xml"))
        assertNull(HubRules.webUrl("tel:10086"))
        assertNull(HubRules.webUrl("sms:10086?body=x"))
        assertNull(HubRules.webUrl("content://contacts/people/1"))
        assertNull(HubRules.webUrl("javascript:alert(1)"))
        assertNull(HubRules.webUrl("pan.example.com"))
        assertNull(HubRules.webUrl(""))
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
    fun `进首页按分钟复验，和上报的小时闸门是两条`() {
        val now = 1_762_000_000_000L
        val minute = 60_000L
        assertTrue(HubRules.shouldRevalidate(0L, now, 30))
        assertFalse(HubRules.shouldRevalidate(now - 29 * minute, now, 30))
        // 边界取等号：正好到点该问。
        assertTrue(HubRules.shouldRevalidate(now - 30 * minute, now, 30))
        assertTrue(HubRules.shouldRevalidate(now - 24 * hour, now, 30))
        // 单位钉死：写成 60_000 之外的数，「半小时」会变成「半小时的 60 倍」或 1/60，
        // 而这两种错在真机上都看不出来，只有流量条会跟着抖。
        assertFalse(HubRules.shouldRevalidate(now - 30 * 1000L, now, 30))
    }

    @Test
    fun `复验间隔的钳制：填错不会自旋，也不会永不问`() {
        assertEquals(5, HubRules.minutes(0))
        assertEquals(5, HubRules.minutes(-3))
        assertEquals(720, HubRules.minutes(100000))
        assertEquals(30, HubRules.minutes(30))
        val now = 1_762_000_000_000L
        // 后台填 0 的实际后果：五分钟一次，而不是每次回到前台都发。
        assertFalse(HubRules.shouldRevalidate(now - 60_000L, now, 0))
        assertTrue(HubRules.shouldRevalidate(now - 6 * 60_000L, now, 0))
    }

    @Test
    fun `带止期的公告到点自己消失，不靠网络`() {
        val now = 1_762_000_000_000L
        val day = 24 * hour
        // 没有止期（0）时行为和以前一模一样。
        assertTrue(HubRules.noticeLive(now - day, now, 0L))
        // 后台宕了、熔断关了、三个月没联网——只要止期过了，这条就该消失。
        assertFalse(HubRules.noticeLive(now - day, now, now - 1000L))
        assertFalse(HubRules.noticeLive(now - day, now, now))
        assertTrue(HubRules.noticeLive(now - day, now, now + hour))
        // 止期不能把「从没确认过」这条兜住：没确认过就没有可信的内容可显示。
        assertFalse(HubRules.noticeLive(0L, now, now + day))
    }

    @Test
    fun `响应里的止期和复验间隔解析成毫秒与分钟`() {
        val body = """{"v":1,"g":7,"notice":{"id":4,"lv":"alert","ti":"下线测试","bd":"正文","ex":1762000000},""" +
            """"rel":null,"pol":{"bm":24,"bv":45,"rp":12,"on":0}}"""
        val config = parseHubConfig(body)
        assertNotNull(config)
        assertEquals(1762000000_000L, config!!.notice?.expMs)
        assertEquals(45, config.policy.revalidateMin)
        assertEquals(12, config.policy.reportHours)
        assertFalse(config.policy.statsOn)
        // 老后台没有 ex/bv：止期当没有，间隔退回默认值，而不是解析失败。
        val legacy = parseHubConfig("""{"v":1,"notice":{"id":1,"ti":"只有标题"},"pol":{"bm":24,"rp":24,"on":1}}""")
        assertEquals(0L, legacy!!.notice?.expMs)
        assertEquals(HubRules.DEFAULT_REVALIDATE_MIN, legacy.policy.revalidateMin)
    }

    @Test
    fun `历史列表解析：已下线的照样留，脏条目丢掉`() {
        val body = """{"v":1,"g":3,"n":[
            {"id":9,"lv":"info","ti":"最新一条","at":1762000000,"on":1},
            {"id":8,"lv":"warn","ti":"已经撤下来","at":1761000000,"on":0},
            {"id":7,"lv":"info","ti":"","bd":"没有标题的脏数据"}
        ]}"""
        val items = parseNoticeList(body)
        assertNotNull(items)
        assertEquals(2, items!!.size)
        assertEquals(9, items[0].id)
        assertTrue(items[0].live)
        assertEquals(1761000000_000L, items[1].pubMs)
        assertFalse(items[1].live)
        // 坏 JSON 退化成 null（界面据此显示缓存），而不是抛出去把列表页搞崩。
        assertNull(parseNoticeList("not json"))
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

    @Test
    fun `脏 id 与脏时间戳不会把公告触达打死`() {
        // id 进的是「打扰名额」的记账（按最大值比较）：一条 id=Int.MAX 的脏数据能让
        // 之后所有真公告永远不弹、永远不进通知栏，而界面上一个原因都看不出来。
        // 时间戳 *1000 溢出会绕成负数，那条公告于是变成「永久过期」。
        val items = parseNoticeList(
            """{"v":1,"n":[
                {"id":2147483647,"ti":"顶格","ex":9223372036854775,"at":-5},
                {"id":-3,"ti":"负数"}
            ]}"""
        )
        assertNotNull(items)
        assertEquals(2, items!!.size)
        assertEquals(1_000_000_000, items[0].id)
        assertEquals(0L, items[0].expMs)
        assertEquals(0L, items[0].pubMs)
        assertEquals(0, items[1].id)
        // 正常的一条不受牵连：公元 2100 年之内照原样换毫秒。
        val sane = parseNoticeList("""{"v":1,"n":[{"id":12,"ti":"正常","at":1762000000}]}""")!!
        assertEquals(12, sane[0].id)
        assertEquals(1762000000_000L, sane[0].pubMs)
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
