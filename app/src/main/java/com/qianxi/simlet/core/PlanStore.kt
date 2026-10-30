package com.qianxi.simlet.core

import android.content.Context
import android.provider.Settings
import com.qianxi.simlet.model.OverridePlan
import com.qianxi.simlet.model.SimSlot

/**
 * 记录每张卡「期望的覆盖内容」，并保证它持续生效。
 *
 * 需要它的原因：persist=false 的覆盖会在运营商配置重载时被系统清掉，
 * persist=true 也不是所有机型都接受。所以除了尽量持久写入，
 * 还要在开机后把丢失的覆盖补回去（同一开机周期内每张卡最多补一次）。
 */
object PlanStore {

    private const val PREFS = "simlet_plans"
    private const val KEY_BOOT = "applied_boot_count"
    private const val KEY_HEALED = "healed_slots"

    fun remember(context: Context, slotIndex: Int, plan: OverridePlan) {
        prefs(context).edit().apply {
            if (plan.empty) {
                remove(field(slotIndex, "iso"))
                remove(field(slotIndex, "name"))
                remove(field(slotIndex, "set"))
            } else {
                putBoolean(field(slotIndex, "set"), true)
                putString(field(slotIndex, "iso"), plan.countryIso.orEmpty())
                putString(field(slotIndex, "name"), plan.carrierName.orEmpty())
            }
        }.apply()
    }

    fun planned(context: Context, slotIndex: Int): OverridePlan? {
        val store = prefs(context)
        if (!store.getBoolean(field(slotIndex, "set"), false)) return null
        val plan = OverridePlan.from(
            store.getString(field(slotIndex, "iso"), null),
            store.getString(field(slotIndex, "name"), null)
        )
        return plan.takeIf { !it.empty }
    }

    /** 把系统里正生效的覆盖纳入跟踪，包括不是本应用写的那些。 */
    fun adopt(context: Context, slots: List<SimSlot>) {
        slots.filter { !it.applied.empty }.forEach { remember(context, it.slotIndex, it.applied) }
    }

    /**
     * 新一次开机时补写丢失的覆盖；同一开机周期内每张卡最多尝试一次。
     * @return 是否做过补写
     */
    fun reapplyAfterBoot(context: Context): Boolean {
        if (!ShizukuLink.granted()) return false
        val bootCount = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        }.getOrDefault(-1)
        if (bootCount < 0) return false

        val store = prefs(context)
        if (store.getInt(KEY_BOOT, -2) == bootCount) return false
        store.edit().putInt(KEY_BOOT, bootCount).remove(KEY_HEALED).apply()

        val slots = SlotReader.slots(context)
        adopt(context, slots)
        val healed = healedSlots(context)

        var reapplied = false
        for (slot in slots) {
            if (slot.slotIndex in healed) continue
            val plan = planned(context, slot.slotIndex) ?: continue
            if (plan != slot.applied && OverrideWriter.apply(context, slot, plan).isSuccess) {
                reapplied = true
            }
            store.edit().putStringSet(KEY_HEALED, (healed + slot.slotIndex).map(Int::toString).toSet()).apply()
        }
        return reapplied
    }

    private fun healedSlots(context: Context): Set<Int> =
        prefs(context).getStringSet(KEY_HEALED, emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun field(slotIndex: Int, suffix: String) = "slot$slotIndex.$suffix"
}
