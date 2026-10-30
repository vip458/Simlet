package com.qianxi.simlet.core

import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.qianxi.simlet.model.SimSlot

/**
 * 设备现状读取：卡槽映射 + 当前生效覆盖。
 *
 * 卡槽→订阅号优先用 shell 侧 `dumpsys isub`，因为公开 SubscriptionManager 需要
 * READ_PHONE_STATE，而本应用不申请任何运行时权限。覆盖内容以
 * `dumpsys carrier_config` 的 mOverrideConfigs 为准。
 */
object SlotReader {

    private val LOGICAL_SLOT = Regex("""Logical SIM slot (\d+):\s*subId=(-?\d+)""")
    private val INTERNAL_BLOCK = Regex("""SubscriptionInfoInternal:\s*id=(\d+)(.*)""")
    private val SLOT_INDEX = Regex("""simSlotIndex=(\d+)""")
    private val CARRIER_NAME = Regex("""carrierName=([^,\]]+)""")
    private val DISPLAY_NAME = Regex("""displayName=([^,\]]+)""")
    private val MCC = Regex("""\bmcc=(\d+)""")
    private val MNC = Regex("""\bmnc=(\d+)""")
    private val COUNTRY_ISO = Regex("""countryIso=([a-z]{2})""")

    fun slots(context: Context): List<SimSlot> {
        val overrides = OverrideDump.read()
        val isub = isubOutput()
        return mapping().toList()
            .sortedBy { it.first }
            .mapNotNull { (slotIndex, subscriptionId) ->
                if (subscriptionId <= 0) return@mapNotNull null
                SimSlot(
                    slotIndex = slotIndex,
                    subscriptionId = subscriptionId,
                    reportedOperator = operatorLabel(context, slotIndex, subscriptionId, isub),
                    applied = overrides[slotIndex]
                        ?: ConfigReader.appliedViaLoader(context, subscriptionId),
                    effectiveCountryIso = recordOf(isub, subscriptionId)?.let { COUNTRY_ISO.find(it)?.groupValues?.get(1) }
                )
            }
    }

    /**
     * SIM 本身所属国家，用于还原时写回真实值。
     *
     * 不能用 isub 里的 countryIso——那个值正是被覆盖污染、需要修正的对象；
     * 框架的 MccTable 在部分 ROM 上对应用不可见，所以退回「当前注册网络的国家码」，
     * 非漫游时它与本卡国家一致。取不到就返回 null，由调用方放弃补写。
     */
    fun nativeCountryIso(slotIndex: Int, subscriptionId: Int): String? {
        val roaming = propList("gsm.operator.isroaming").getOrNull(slotIndex)?.toBooleanStrictOrNull()
        if (roaming == true) return null
        propList("gsm.operator.iso-country").getOrNull(slotIndex)?.let { iso ->
            if (Regex("""[a-z]{2}""").matches(iso)) return iso
        }
        val mcc = recordOf(isubOutput(), subscriptionId)?.let { MCC.find(it)?.groupValues?.get(1) } ?: return null
        // MccTable 是隐藏类：应用进程里不先解除限制，反射会直接拿不到
        HiddenAccess.ensure()
        return runCatching {
            Class.forName("android.telephony.MccTable")
                .getMethod("countryCodeForMcc", String::class.java)
                .invoke(null, mcc) as? String
        }.getOrNull()?.lowercase()?.takeIf { it.length == 2 }
    }

    /** 多卡系统属性形如 `cn,cn`，按卡槽序号取自己那一项。 */
    private fun propList(key: String): List<String> =
        runCatching {
            ShizukuLink.exec("getprop $key").stdout.trim().trimStart('[').trimEnd(']')
        }.getOrNull()?.split(',')?.map(String::trim)?.filter(String::isNotEmpty) ?: emptyList()

    private fun recordOf(isub: String?, subscriptionId: Int): String? {
        if (isub == null) return null
        INTERNAL_BLOCK.findAll(isub).forEach { match ->
            if (match.groupValues[1].toIntOrNull() == subscriptionId) return match.groupValues[2]
        }
        return null
    }

    /** 卡槽序号（等于 Phone Id）→ 订阅号。 */
    fun mapping(): Map<Int, Int> {
        val fromShell = slotsFromDumpsys()
        return if (fromShell.isNotEmpty()) fromShell else emptyMap()
    }

    private fun slotsFromDumpsys(): Map<Int, Int> {
        val output = isubOutput() ?: return emptyMap()
        val result = linkedMapOf<Int, Int>()

        LOGICAL_SLOT.findAll(output).forEach { match ->
            val slot = match.groupValues[1].toIntOrNull() ?: return@forEach
            val subId = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (subId > 0) result[slot] = subId
        }
        // 部分 ROM 不打印 Logical SIM slot 行，退回从内部订阅块里配对
        INTERNAL_BLOCK.findAll(output).forEach { match ->
            val subId = match.groupValues[1].toIntOrNull() ?: return@forEach
            val slot = SLOT_INDEX.find(match.groupValues[2])?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            if (subId > 0 && slot >= 0) result.putIfAbsent(slot, subId)
        }
        return result
    }

    /**
     * 状态栏与系统上报的运营商名，用于标注「这是哪张卡」。
     *
     * 返回 null 表示读不到，由界面给本地化的「未知运营商」；MCC/MNC 兜底只回稳定 id，
     * 显示名交给界面按语言映射，否则英文界面下会露出中文。
     */
    private fun operatorLabel(context: Context, slotIndex: Int, subscriptionId: Int, isub: String?): String? {
        isubName(subscriptionId, isub)?.let { return it }

        val manager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val info = runCatching {
            manager?.getActiveSubscriptionInfo(subscriptionId)
                ?: manager?.getActiveSubscriptionInfoForSimSlotIndex(slotIndex)
        }.getOrNull()
        info?.displayName?.toString()?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        info?.carrierName?.toString()?.takeIf { it.isNotBlank() }?.let { return it.trim() }

        return runCatching {
            context.getSystemService(TelephonyManager::class.java)
                ?.createForSubscriptionId(subscriptionId)
                ?.simOperatorName
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()?.trim()
    }

    private fun isubName(subscriptionId: Int, isub: String?): String? {
        val body = recordOf(isub, subscriptionId) ?: return null
        val numeric = MCC.find(body)?.groupValues?.get(1)?.let { mcc ->
            MNC.find(body)?.groupValues?.get(1)?.let { mnc -> mcc + mnc.padStart(2, '0') }
        }
        return numeric?.let(::carrierIdByNumeric)
            ?: DISPLAY_NAME.find(body)?.groupValues?.get(1)?.trim()?.trimEnd { it.isDigit() }?.takeIf { it.isNotEmpty() }
            ?: CARRIER_NAME.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun isubOutput(): String? =
        runCatching { ShizukuLink.exec("dumpsys isub").stdout }.getOrNull()

    /** 国内四家运营商的稳定 id；显示名由界面按语言映射，这里不出文案。 */
    private fun carrierIdByNumeric(numeric: String): String? = when {
        numeric.startsWith("46015") -> "cbn"
        numeric.startsWith("46003") || numeric.startsWith("46005") || numeric.startsWith("46011") -> "ctcc"
        numeric.startsWith("46001") || numeric.startsWith("46006") || numeric.startsWith("46009") -> "cucc"
        numeric.startsWith("46000") || numeric.startsWith("46002") || numeric.startsWith("46007") ||
            numeric.startsWith("46008") -> "cmcc"

        else -> null
    }
}
