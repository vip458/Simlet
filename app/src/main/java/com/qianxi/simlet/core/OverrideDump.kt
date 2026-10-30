package com.qianxi.simlet.core

/**
 * 以 `dumpsys carrier_config` 的 `mOverrideConfigs` 为准读取覆盖内容。
 *
 * 这是唯一能区分「系统本来就有的值」和「被覆盖写入的值」的通道；
 * 从 CarrierConfigManager 读回的是合并结果，无法判断是否真被覆盖。
 */
internal object OverrideDump {

    private val PHONE_ID = Regex("""^Phone Id = (\d+)""")
    private val OVERRIDE_HEAD = "mOverrideConfigs"
    private val KEYS = setOf(
        "sim_country_iso_override_string",
        "carrier_name_override_bool",
        "carrier_name_string"
    )

    /** @return 卡槽序号（即 Phone Id）到覆盖内容的映射；无覆盖的卡槽值为空计划 */
    fun read(): Map<Int, com.qianxi.simlet.model.OverridePlan> {
        val output = runCatching { ShizukuLink.exec("dumpsys carrier_config").stdout }.getOrNull()
            ?: return emptyMap()
        return parse(output)
    }

    internal fun parse(output: String): Map<Int, com.qianxi.simlet.model.OverridePlan> {
        val result = mutableMapOf<Int, com.qianxi.simlet.model.OverridePlan>()
        var phoneId: Int? = null
        var insideOverride = false
        val raw = mutableMapOf<String, String>()

        fun flush() {
            val id = phoneId ?: return
            // 块尾与解析结尾各会 flush 一次，第二次时暂存键已清空；
            // 若不挡住，最后一张卡刚记下的覆盖会被空计划就地盖掉。
            if (raw.isEmpty() && id in result) return
            val iso = raw["sim_country_iso_override_string"]?.takeIf { it.isNotBlank() }
            val name = raw["carrier_name_string"]?.takeIf {
                it.isNotBlank() && raw["carrier_name_override_bool"] == "true"
            }
            result[id] = com.qianxi.simlet.model.OverridePlan.from(iso, name)
            raw.clear()
        }

        output.lineSequence().forEach { line ->
            val trimmed = line.trim()

            PHONE_ID.find(trimmed)?.let { match ->
                flush()
                phoneId = match.groupValues[1].toIntOrNull()
                insideOverride = false
                return@forEach
            }

            if (trimmed.startsWith(OVERRIDE_HEAD)) {
                flush()
                insideOverride = true
                return@forEach
            }

            // 覆盖块内遇到下一个成员行即结束；空行跳过
            if (trimmed.isEmpty()) return@forEach
            if (trimmed.startsWith("m") && trimmed.endsWith(":")) {
                if (insideOverride) flush()
                insideOverride = false
                return@forEach
            }
            if (!insideOverride || phoneId == null) return@forEach

            val separator = trimmed.indexOf(" = ")
            if (separator <= 0) return@forEach
            val key = trimmed.substring(0, separator)
            if (key in KEYS) {
                raw[key] = trimmed.substring(separator + 3).trim()
            }
        }

        flush()
        return result
    }
}
