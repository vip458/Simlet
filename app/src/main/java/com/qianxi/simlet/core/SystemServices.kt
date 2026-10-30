package com.qianxi.simlet.core

import android.content.Context
import android.os.IBinder

/**
 * 系统服务取用：全部通过反射触碰非公开入口，调用前先 [HiddenAccess.ensure]。
 */
internal object SystemServices {

    private val serviceManager: Class<*> by lazy { Class.forName("android.os.ServiceManager") }
    private val getService: java.lang.reflect.Method by lazy {
        serviceManager.getMethod("getService", String::class.java)
    }

    /** 按 AOSP 服务名取 binder；未注册时返回 null。 */
    fun binder(name: String): IBinder? = runCatching { getService.invoke(null, name) as? IBinder }.getOrNull()

    fun activityBinder(): IBinder? = binder(Context.ACTIVITY_SERVICE)

    /** IActivityManager 代理，默认已包装为 shell 身份。 */
    fun activityManager(): Any? = runCatching {
        val raw = activityBinder() ?: return null
        val stub = Class.forName("android.app.IActivityManager\$Stub")
        stub.getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuLink.asShell(raw))
    }.getOrNull()

    /** 通用 binder 接口代理，用于 phone / carrier_config / isub 等服务。 */
    fun proxy(serviceName: String, stubClassName: String): Any? = runCatching {
        val raw = binder(serviceName) ?: return null
        Class.forName(stubClassName)
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuLink.asShell(raw))
    }.getOrNull()

    /** 不包装的裸代理：调用方以本应用 UID 出现，配合权限委托使用。 */
    fun proxyAsSelf(serviceName: String, stubClassName: String): Any? = runCatching {
        val raw = binder(serviceName) ?: return null
        Class.forName(stubClassName)
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, raw)
    }.getOrNull()

    /** 反射调用并把 InvocationTargetException 还原成真实异常，便于按错误信息分级回退。 */
    fun invoke(method: java.lang.reflect.Method, target: Any, vararg args: Any?): Any? =
        try {
            method.invoke(target, *args)
        } catch (wrapped: java.lang.reflect.InvocationTargetException) {
            throw wrapped.targetException ?: wrapped
        }

    /** 在代理对象上按名字+签名取方法；AIDL 生成类的方法都在接口上，可直接 getMethod。 */
    fun method(target: Any, name: String, vararg types: Class<*>): java.lang.reflect.Method? =
        runCatching { target.javaClass.getMethod(name, *types) }.getOrNull()

    fun unwrap(error: Throwable): Throwable {
        var current = error
        while (current is java.lang.reflect.InvocationTargetException && current.cause != null) {
            current = current.cause!!
        }
        return current
    }
}
