package com.qianxi.simlet.model

/**
 * 一张物理 SIM 的当前视图。
 *
 * @param slotIndex 0 基卡槽序号
 * @param subscriptionId 系统订阅号，写入以它为目标
 * @param reportedOperator 这张卡真实的运营商名（不随覆盖变化）；读不到为 null，由界面给本地化文案
 * @param applied 当前已生效的覆盖内容
 * @param effectiveCountryIso 系统当前为它派生出的国家码，也就是应用真正读到的值
 */
data class SimSlot(
    val slotIndex: Int,
    val subscriptionId: Int,
    val reportedOperator: String?,
    val applied: OverridePlan,
    val effectiveCountryIso: String? = null
) {
    /** 1 基卡槽号；「卡 N」这类显示串一律由界面按语言拼，模型不出文案。 */
    val slotNumber: Int get() = slotIndex + 1
    val overridden: Boolean get() = !applied.empty
}
