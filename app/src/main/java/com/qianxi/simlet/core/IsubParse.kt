package com.qianxi.simlet.core

/**
 * `dumpsys isub` 的文本解析。单独放一个文件、不碰任何 Android 类型，
 * 是因为这段逻辑一旦读不出来，用户看到的就是「识别不到卡」，
 * 而各家 ROM 的打印格式又不一样——它必须能被测试覆盖。
 */
internal object IsubParse {

    private val LOGICAL_SLOT = Regex("""Logical SIM slot (\d+):\s*subId=(-?\d+)""")
    private val SUB_ID = Regex("""\bid=(\d+)""")
    private val SLOT_INDEX = Regex("""\bsimSlotIndex=(\d+)""")

    /**
     * 卡槽序号（等于 phoneId）→ 订阅号。
     *
     * 先认 AOSP 的 `Logical SIM slot N: subId=M`，没有这种行再逐行配对 `id=` 与
     * `simSlotIndex=`。这里刻意**不**要求行里出现某个记录类名：红米打的是
     * `MtkSubscriptionInfoInternal`，别家可能打 `SubscriptionInfo{...}`，
     * 一旦按类名匹配就会整台机读不到卡。`simSlotIndex=-1` 的空槽与残留记录
     * 配不上数字，天然被跳过。
     */
    fun mapping(output: String): Map<Int, Int> {
        val result = linkedMapOf<Int, Int>()
        LOGICAL_SLOT.findAll(output).forEach { match ->
            val slot = match.groupValues[1].toIntOrNull() ?: return@forEach
            val subId = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (subId > 0) result[slot] = subId
        }
        output.lineSequence().forEach { line ->
            val slot = SLOT_INDEX.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            SUB_ID.findAll(line)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .filter { it > 0 }
                .forEach { result.putIfAbsent(slot, it) }
        }
        return result
    }

    /** 某个订阅号所在的那一行，用于再取 countryIso / 运营商名等字段。 */
    fun record(output: String, subscriptionId: Int): String? = output.lineSequence().firstOrNull { line ->
        SUB_ID.findAll(line).any { it.groupValues[1].toIntOrNull() == subscriptionId }
    }
}
