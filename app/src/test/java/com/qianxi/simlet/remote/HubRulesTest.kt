package com.qianxi.simlet.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 公告这条链路上会真的影响行为的判断都在这里钉住。
 *
 * 挑的不是「能跑」，而是三类出问题时用户看不见的地方：
 * 地址填错到底是静默关闭还是明文发出去、后台宕了会不会每次启动都白等一个超时、
 * 一条过期公告会不会一直挂着。
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
}
