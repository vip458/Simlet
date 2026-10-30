package com.qianxi.simlet.core

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import com.qianxi.simlet.model.OverridePlan

/**
 * 通道三的启动器：请求 system_server（以 shell 身份）在我们包内拉起 [SimletOverrideJob]。
 *
 * startInstrumentation 的签名在不同 Android 版本间变过几次，这里按参数类型逐个装配，
 * 以适配从 API 26 到 37 的各个重载。
 */
internal object JobLauncher {

    fun launch(context: Context, subscriptionId: Int, plan: OverridePlan): Boolean {
        val manager = SystemServices.activityManager() ?: return false
        val arguments = Bundle().apply {
            putInt(SimletOverrideJob.KEY_SUB_ID, subscriptionId)
            plan.countryIso?.let { putString(SimletOverrideJob.KEY_COUNTRY_ISO, it) }
            plan.carrierName?.let { putString(SimletOverrideJob.KEY_CARRIER_NAME, it) }
        }
        val component = ComponentName(context, SimletOverrideJob::class.java)

        val candidates = manager.javaClass.methods
            .filter { it.name == "startInstrumentation" }
            .sortedByDescending { it.parameterCount }

        for (method in candidates) {
            val built = buildArguments(method, component, arguments) ?: continue
            if (runCatching { SystemServices.invoke(method, manager, *built) }.isSuccess) return true
        }
        return false
    }

    private fun buildArguments(
        method: java.lang.reflect.Method,
        component: ComponentName,
        arguments: Bundle
    ): Array<Any?>? {
        val types = method.parameterTypes
        if (types.isEmpty() || types[0] != ComponentName::class.java) return null

        return Array(types.size) { index ->
            when (types[index]) {
                ComponentName::class.java -> component
                Bundle::class.java -> arguments
                // 第二个 int 是 userId，其余 int 位是 instrumentation flags
                Int::class.javaPrimitiveType -> if (index == types.size - 2) currentUserId() else instrumentationFlags()
                String::class.java -> null
                else -> automationConnection(types[index])
            }
        }
    }

    /** 不复用已有应用进程 + 关掉隐藏接口校验，两者缺一 job 就跑不起来。 */
    private fun instrumentationFlags(): Int = runCatching {
        val activityManager = Class.forName("android.app.ActivityManager")
        flagOf(activityManager, "INSTR_FLAG_NO_RESTART") or
            flagOf(activityManager, "INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS")
    }.getOrDefault(0)

    private fun flagOf(clazz: Class<*>, name: String): Int = runCatching {
        clazz.getField(name).getInt(null)
    }.getOrDefault(0)

    private fun currentUserId(): Int = runCatching {
        Class.forName("android.os.UserHandle").getMethod("myUserId").invoke(null) as Int
    }.getOrDefault(0)

    private fun automationConnection(type: Class<*>): Any? {
        if (!type.name.endsWith("IUiAutomationConnection")) return null
        return runCatching {
            Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance()
        }.getOrNull()
    }
}
