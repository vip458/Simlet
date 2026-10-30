package com.qianxi.simlet.core

import android.content.Context
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager

/**
 * 通道二：借到 shell 权限身份后，用公开类 [CarrierConfigManager] 的隐藏实例方法写入。
 * 这是 Android 16/17 上的主路径，全部在本进程内完成，不会触发系统 force-stop。
 */
internal object ManagerChannel {

    fun read(context: Context, subscriptionId: Int): PersistableBundle? {
        val manager = carrierManager(context) ?: return null
        return runCatching { manager.getConfigForSubId(subscriptionId) }.getOrNull()
    }

    /**
     * @return 系统是否接受了本次覆盖请求
     */
    fun write(context: Context, subscriptionId: Int, config: PersistableBundle?, persist: Boolean): Boolean {
        HiddenAccess.ensure()
        val manager = carrierManager(context)
            ?: throw LoaderChannel.Unavailable("CarrierConfigManager 不可用")
        val method = SystemServices.method(
            manager,
            "overrideConfig",
            Int::class.javaPrimitiveType!!,
            PersistableBundle::class.java,
            Boolean::class.javaPrimitiveType!!
        ) ?: throw LoaderChannel.Unavailable("当前系统未暴露 overrideConfig")
        val accepted = SystemServices.invoke(method, manager, subscriptionId, config, persist)
        return accepted as? Boolean ?: true
    }

    private fun carrierManager(context: Context): CarrierConfigManager? =
        context.getSystemService(CarrierConfigManager::class.java)
}
