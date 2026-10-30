package com.qianxi.simlet.core

/**
 * 读取系统当前实际上报给应用的运营商名（service state 里的 `mOperatorAlphaLong`）。
 *
 * 品牌覆盖是否被用户看见，只看这个字段：它由 getOperatorBrandOverride 的结果改写。
 * dumpsys carrier_config 里只有配置值，SubscriptionInfo 又不跟随覆盖，两者都判不出可见效果。
 */
internal object RegistryDump {

    /** 只保留「每张卡的当前状态」两行，避免把几十 KB 的事件日志读进内存。 */
    private const val COMMAND =
        "dumpsys telephony.registry | grep -E \"Phone Id=|mServiceState=\""

    private val PHONE_ID = Regex("""Phone Id\s*=\s*(\d+)""")
    private val ALPHA_LONG = Regex("""mOperatorAlphaLong=([^,]*)""")

    /** @return phoneId（即卡槽序号）到当前上报运营商名的映射 */
    fun effectiveNames(): Map<Int, String> =
        runCatching { ShizukuLink.exec(COMMAND).stdout }
            .map { parse(it) }
            .getOrDefault(emptyMap())

    fun effectiveName(phoneId: Int): String? = effectiveNames()[phoneId]

    internal fun parse(output: String): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        var phoneId: Int? = null
        output.lineSequence().forEach { line ->
            PHONE_ID.find(line)?.let { match ->
                phoneId = match.groupValues[1].toIntOrNull()
                return@forEach
            }
            val id = phoneId ?: return@forEach
            if (id in result) return@forEach
            ALPHA_LONG.find(line)?.let { match -> result[id] = match.groupValues[1].trim() }
        }
        return result
    }
}
