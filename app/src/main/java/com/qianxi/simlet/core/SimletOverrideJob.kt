package com.qianxi.simlet.core

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import com.qianxi.simlet.model.OverridePlan

/**
 * 通道三：由 Shizuku 以 shell 身份拉起的独立 instrumentation 进程。
 *
 * 用于兜底「主进程内委托身份失败」的机型——部分 OEM ROM 只允许刚启动的进程拿到委托。
 * 进程内做的事与通道二完全一致，只是执行环境不同。
 */
class SimletOverrideJob : Instrumentation() {

    override fun onCreate(arguments: Bundle?) {
        HiddenAccess.ensure()
        val args = arguments ?: Bundle()
        val subscriptionId = args.intOf(KEY_SUB_ID, default = -1)
        val iso = args.stringOf(KEY_COUNTRY_ISO)?.takeIf { it.length == 2 }
        val carrier = args.stringOf(KEY_CARRIER_NAME)?.takeIf { it.isNotBlank() }

        if (subscriptionId < 0) {
            finishWithError("订阅号无效")
            return
        }

        // 必须在子线程里干活并 finish：占住主线程会被 HyperOS 判成应用卡死并留下痕迹。
        Thread({
            var failure: Throwable? = null
            try {
                // 先等 targetContext：应用与它的 ContentProvider 装好后 Shizuku 才可能把 binder 递进来，
                // 顺序颠倒时全新进程里 pingBinder 会一直为 false。
                val target = awaitTargetContext()
                awaitShizuku()
                val plan = OverridePlan.from(iso, carrier)
                val bundle = plan.toBundle().takeIf { !plan.empty }
                ShellIdentity.runAsShell(target, ShellIdentity.MODEM_PERMISSIONS) {
                    ManagerChannel.write(target, subscriptionId, bundle, persist = false)
                    LoaderChannel.notifyChanged(subscriptionId)
                    SubscriptionSync.sync(target, subscriptionId, plan.carrierName)
                }
            } catch (error: Throwable) {
                failure = error
            } finally {
                val error = failure
                if (error == null) {
                    finish(Activity.RESULT_OK, Bundle())
                } else {
                    finishWithError(SystemServices.unwrap(error).message)
                }
            }
        }, "SimletJob").start()
    }

    private fun finishWithError(message: String?) {
        finish(Activity.RESULT_FIRST_USER, Bundle().apply { putString(KEY_ERROR, message ?: "写入失败") })
    }

    private fun awaitTargetContext(timeoutMs: Long = 8_000L): Context {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            targetContext?.let { return it.applicationContext }
            Thread.sleep(60)
        }
        error("targetContext 未就绪")
    }

    /**
     * 只等 binder 到达，不在这里校验授权：
     * 全新进程里 checkSelfPermission 会先于服务端的授权握手返回拒绝，
     * 拿它当门槛会把本来能写成功的机型挡在门外；真没授权时写入自己会报错。
     */
    private fun awaitShizuku(timeoutMs: Long = 15_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (ShizukuLink.binderAlive()) return
            Thread.sleep(60)
        }
        error("Shizuku 未就绪")
    }

    companion object {
        const val KEY_SUB_ID = "simlet.SUB_ID"
        const val KEY_COUNTRY_ISO = "simlet.COUNTRY_ISO"
        const val KEY_CARRIER_NAME = "simlet.CARRIER_NAME"
        const val KEY_ERROR = "simlet.ERROR"
    }
}

/** am instrument -e 传进来的参数全是字符串，进程内启动则传原生类型，两种都要能吃下。 */
private fun Bundle.intOf(key: String, default: Int): Int = when (val value = get(key)) {
    is Int -> value
    is Long -> value.toInt()
    is String -> value.toIntOrNull() ?: default
    else -> default
}

private fun Bundle.stringOf(key: String): String? =
    (get(key) as? String)?.takeIf { it.isNotBlank() && it != "null" }
