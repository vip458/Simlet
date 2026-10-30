package com.qianxi.simlet.core

import android.os.PersistableBundle
import android.telephony.CarrierConfigManager

/**
 * 通道一：直接对 ICarrierConfigLoader 发 binder 调用。
 * shell 身份即可调用，代价是 Android 16 起该入口被收紧，多数新机会抛
 * "cannot be invoked by shell"，此时必须回退到通道二。
 */
internal object LoaderChannel {

    private const val SERVICE = "carrier_config"
    private const val STUB = "com.android.internal.telephony.ICarrierConfigLoader\$Stub"

    fun available(): Boolean = proxy() != null

    /** 读取某个订阅号当前生效的配置；shell 身份读取无需额外权限。 */
    fun read(subscriptionId: Int, callingPackage: String): PersistableBundle? {
        val loader = proxy() ?: return null
        val method = SystemServices.method(
            loader,
            "getConfigForSubId",
            Int::class.javaPrimitiveType!!,
            String::class.java
        ) ?: return null
        return SystemServices.invoke(method, loader, subscriptionId, callingPackage) as? PersistableBundle
    }

    fun write(subscriptionId: Int, config: PersistableBundle?, persist: Boolean) {
        val loader = proxy() ?: throw Unavailable("CarrierConfigLoader 不可用")
        val method = SystemServices.method(
            loader,
            "overrideConfig",
            Int::class.javaPrimitiveType!!,
            PersistableBundle::class.java,
            Boolean::class.javaPrimitiveType!!
        ) ?: throw Unavailable("当前系统未暴露 overrideConfig")
        SystemServices.invoke(method, loader, subscriptionId, config, persist)
    }

    /**
     * 不经过 Shizuku 包装的直连写入，只能在 shell 权限委托会话内使用。
     *
     * 包装后的 binder 会被还原成 shell 身份，而 Android 16 起 shell 已被禁止写覆盖；
     * 裸 binder 让服务端看到的是本应用 UID —— 配合委托借到的 MODIFY_PHONE_STATE 才通得过校验。
     */
    fun writeAsSelf(subscriptionId: Int, config: PersistableBundle?, persist: Boolean) {
        val loader = selfProxy() ?: throw Unavailable("CarrierConfigLoader 不可用")
        val method = SystemServices.method(
            loader,
            "overrideConfig",
            Int::class.javaPrimitiveType!!,
            PersistableBundle::class.java,
            Boolean::class.javaPrimitiveType!!
        ) ?: throw Unavailable("当前系统未暴露 overrideConfig")
        SystemServices.invoke(method, loader, subscriptionId, config, persist)
    }

    fun readAsSelf(subscriptionId: Int, callingPackage: String): PersistableBundle? {
        val loader = selfProxy() ?: return null
        val method = SystemServices.method(
            loader,
            "getConfigForSubId",
            Int::class.javaPrimitiveType!!,
            String::class.java
        ) ?: return null
        return SystemServices.invoke(method, loader, subscriptionId, callingPackage) as? PersistableBundle
    }

    fun notifyChanged(subscriptionId: Int) {
        val loader = proxy() ?: return
        val method = SystemServices.method(
            loader,
            "notifyConfigChangedForSubId",
            Int::class.javaPrimitiveType!!
        ) ?: return
        runCatching { SystemServices.invoke(method, loader, subscriptionId) }
    }

    /** 覆盖内容里读回的运营商名，仅当 override 开关为真。 */
    fun carrierNameOf(config: PersistableBundle?): String? {
        if (config == null) return null
        val enabled = runCatching {
            config.getBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, false)
        }.getOrDefault(false)
        if (!enabled) return null
        return config.getString(CarrierConfigManager.KEY_CARRIER_NAME_STRING)
    }

    fun countryIsoOf(config: PersistableBundle?): String? =
        config?.getString(CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING)

    private fun proxy(): Any? = SystemServices.proxy(SERVICE, STUB)

    private fun selfProxy(): Any? = SystemServices.proxyAsSelf(SERVICE, STUB)

    class Unavailable(message: String) : IllegalStateException(message)
}
