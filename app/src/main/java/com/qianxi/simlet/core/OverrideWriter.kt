package com.qianxi.simlet.core

import android.content.Context
import android.os.PersistableBundle
import android.util.Log
import com.qianxi.simlet.model.OverridePlan
import com.qianxi.simlet.model.SimSlot

/**
 * 写入总控：按「Loader 直调 → shell 身份委托 → 后台 instrumentation」逐级回退。
 *
 * 每一级都写会话级覆盖，并读回校验确认真正生效；生效后再让运营商名跟随。
 */
object OverrideWriter {

    private const val TAG = "SimletWriter"
    private const val QUICK_VERIFY_MS = 4_000L
    private const val SLOW_VERIFY_MS = 20_000L

    /** [plan] 为空计划即表示还原。 */
    fun apply(context: Context, slot: SimSlot, plan: OverridePlan): Result<Unit> {
        val appContext = context.applicationContext
        val bundle = plan.toBundle().takeIf { !plan.empty }
        var lastError: Throwable? = null
        Log.i(TAG, "写入 slot=${slot.slotIndex} sub=${slot.subscriptionId} plan=$plan")

        val attempts = listOf(
            "通道一" to { directLoaderWrite(appContext, slot, bundle, plan) },
            "通道二" to { delegatedWrite(appContext, slot, bundle, plan) },
            "通道三" to { backgroundJobWrite(appContext, slot, plan) }
        )

        for ((name, attempt) in attempts) {
            runCatching(attempt)
                .onSuccess {
                    Log.i(TAG, "$name 写入生效")
                    return Result.success(Unit)
                }
                .onFailure { error ->
                    Log.d(TAG, "$name 失败: ${error.message}")
                    lastError = error
                }
        }
        return Result.failure(lastError ?: IllegalStateException("配置写入失败"))
    }

    /**
     * 还原 = 先把该卡真实国家码写回去，再清空覆盖。
     *
     * 系统只在「写入新覆盖」时重算派生出来的 countryIso；直接清空只删掉覆盖记录，
     * 应用会继续读到上一次的国家码（实测：清空后 isub 仍是 jp，开关一次飞行模式也不重算）。
     * 先写真实值让系统重算，再清空，派生值会停在真值上。
     */
    fun restore(context: Context, slot: SimSlot): Result<Unit> {
        val appContext = context.applicationContext
        val nativeIso = SlotReader.nativeCountryIso(slot.slotIndex, slot.subscriptionId)
        val effective = slot.effectiveCountryIso?.lowercase()
        Log.i(TAG, "还原 slot=${slot.slotIndex} sub=${slot.subscriptionId} 系统读到=$effective 真实=$nativeIso")
        // 只要系统读到的还是旧国家码就补一次写入（覆盖已清空但派生值卡住的情况也要能修好）
        if (nativeIso != null && effective != null && effective != nativeIso) {
            val recomputed = apply(appContext, slot, OverridePlan(countryIso = nativeIso))
            if (recomputed.isFailure) return recomputed
        }
        return apply(appContext, slot, OverridePlan())
    }

    private fun directLoaderWrite(
        context: Context,
        slot: SimSlot,
        bundle: PersistableBundle?,
        plan: OverridePlan
    ) {
        LoaderChannel.write(slot.subscriptionId, bundle, persist = false)
        verify(context, slot, plan, QUICK_VERIFY_MS)
        SubscriptionSync.sync(context, slot.subscriptionId, plan.carrierName)
    }

    private fun delegatedWrite(
        context: Context,
        slot: SimSlot,
        bundle: PersistableBundle?,
        plan: OverridePlan
    ) {
        ShellIdentity.runAsShell(context, ShellIdentity.MODEM_PERMISSIONS) {
            // 以本应用 UID 直连 loader：服务端看到的是「借到 MODIFY_PHONE_STATE 的我们」，
            // 而不是已被封堵的 shell；本地 CarrierConfigManager 的自检会拦那一步，故不走它。
            LoaderChannel.writeAsSelf(slot.subscriptionId, bundle, persist = false)
            verify(context, slot, plan, QUICK_VERIFY_MS)
            SubscriptionSync.sync(context, slot.subscriptionId, plan.carrierName)
        }
    }

    /** 名称同步由后台进程自己完成，这里只等配置落地，避免主进程再等一轮重试。 */
    private fun backgroundJobWrite(context: Context, slot: SimSlot, plan: OverridePlan) {
        if (!JobLauncher.launch(context, slot.subscriptionId, plan)) {
            throw IllegalStateException("无法拉起后台写入进程")
        }
        verify(context, slot, plan, SLOW_VERIFY_MS)
    }

    /**
     * 覆盖一律用会话级（persist=false）。
     *
     * 实测 Android 16/17 上普通应用请求 persist=true 有两种下场：直接被拒，或「收下但不落盘」——
     * 读回能看到值，下一次配置重载却整个丢掉，比会话级更难判断。
     * 丢失后由 [PlanStore.reapplyAfterBoot] 在开机时补写。
     */
    private fun verify(context: Context, slot: SimSlot, expected: OverridePlan, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (ConfigReader.matches(context, slot, expected)) return
            Thread.sleep(400)
        }
        if (ConfigReader.matches(context, slot, expected)) return
        throw IllegalStateException(if (expected.empty) "还原未生效，请稍后刷新" else "配置未及时生效，请稍后刷新")
    }
}
