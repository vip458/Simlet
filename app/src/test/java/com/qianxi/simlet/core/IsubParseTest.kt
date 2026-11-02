package com.qianxi.simlet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `dumpsys isub` 各家 ROM 打印格式不同，这里每种格式钉一条：
 * 解析不出卡槽的用户看到的是「识别不到可用 SIM 卡」，而原因完全在这段纯字符串逻辑里。
 * 样本是脱敏改写过的，字段顺序与真实输出一致。
 */
class IsubParseTest {

    @Test
    fun `AOSP 风格，带 Logical SIM slot 行`() {
        val output = """
            SubscriptionManagerService state:
            Logical SIM slot 0: subId=6
            Logical SIM slot 1: subId=7
            [SubscriptionInfoInternal: id=6 simSlotIndex=0 carrierName=中国联通 countryIso=cn]
            [SubscriptionInfoInternal: id=7 simSlotIndex=1 carrierName=中国联通 countryIso=cn]
        """.trimIndent()
        assertEquals(mapOf(0 to 6, 1 to 7), IsubParse.mapping(output))
    }

    @Test
    fun `MTK 风格，没有 Logical 行，记录名前缀是 Mtk`() {
        val output = """
            Dumpging subscription info:
            [MtkSubscriptionInfoInternal: isEnhancedVonrShowUiEnabled=-1][SubscriptionInfoInternal: id=6 iccId=8986[****] simSlotIndex=0 carrierId=1436 displayName=中国联通 carrierName=中国联通 countryIso=cn profileClass=-1]
            [MtkSubscriptionInfoInternal: isEnhancedVonrShowUiEnabled=-1][SubscriptionInfoInternal: id=7 iccId=8986[****] simSlotIndex=1 carrierId=1436 displayName=中国联通 carrierName=中国联通 countryIso=cn profileClass=-1]
        """.trimIndent()
        assertEquals(mapOf(0 to 6, 1 to 7), IsubParse.mapping(output))
    }

    /** 旧实现要求行里出现 `SubscriptionInfoInternal:`，这种花括号写法就整台机读不到卡。 */
    @Test
    fun `记录名换成 SubscriptionInfo 花括号写法也要认得`() {
        val output = """
            Live subscriptions:
              SubscriptionInfo{ id=3 iccId=8988[****] simSlotIndex=1 carrierName=CMHK countryIso=hk}
        """.trimIndent()
        assertEquals(mapOf(1 to 3), IsubParse.mapping(output))
    }

    @Test
    fun `空槽与残留记录的 simSlotIndex 是 -1，不映射`() {
        val output = """
            [SubscriptionInfoInternal: id=1 simSlotIndex=-1 carrierName=中国联通 countryIso=cn]
            [SubscriptionInfoInternal: id=2 simSlotIndex=-1 carrierName=中国电信 countryIso=cn]
        """.trimIndent()
        assertEquals(emptyMap<Int, Int>(), IsubParse.mapping(output))
    }

    @Test
    fun `输出里没有订阅记录时返回空映射，由界面给诊断串`() {
        assertEquals(emptyMap<Int, Int>(), IsubParse.mapping("No subscription info available"))
    }

    @Test
    fun `record 取回该订阅号的整行，取不到时返回 null`() {
        val output = """
            header line id=99 simSlotIndex=9
            [SubscriptionInfoInternal: id=6 simSlotIndex=0 mcc=460 mnc=01 countryIso=cn]
        """.trimIndent()
        val line = IsubParse.record(output, 6) ?: error("取不到记录")
        assert(line.contains("countryIso=cn"))
        assertNull(IsubParse.record(output, 404))
    }
}
