package com.qianxi.simlet.core

import android.content.Context
import com.qianxi.simlet.model.OverridePlan
import com.qianxi.simlet.model.SimSlot

/**
 * 读取某个订阅号当前的覆盖内容，用于写入后的生效校验。
 *
 * 首选 `dumpsys carrier_config` 的 mOverrideConfigs（能区分真实覆盖与系统默认值），
 * 拿不到时退回 Loader 的合并结果。
 */
internal object ConfigReader {

    fun applied(context: Context, slot: SimSlot): OverridePlan =
        OverrideDump.read()[slot.slotIndex] ?: appliedViaLoader(context, slot.subscriptionId)

    /** 写入后校验：只比对本次写入涉及的那几项。 */
    fun matches(context: Context, slot: SimSlot, expected: OverridePlan): Boolean {
        val current = applied(context, slot)
        if (expected.empty) return current.empty
        return (expected.countryIso == null || current.countryIso == expected.countryIso) &&
            (expected.carrierName == null || current.carrierName == expected.carrierName)
    }

    fun appliedViaLoader(context: Context, subscriptionId: Int): OverridePlan {
        val bundle = runCatching {
            LoaderChannel.read(subscriptionId, context.packageName)
        }.getOrNull() ?: runCatching {
            ManagerChannel.read(context, subscriptionId)
        }.getOrNull() ?: return OverridePlan()

        return OverridePlan.from(
            LoaderChannel.countryIsoOf(bundle),
            LoaderChannel.carrierNameOf(bundle)
        )
    }
}
