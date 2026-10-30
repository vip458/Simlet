package com.qianxi.simlet.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

/**
 * 第二级通道：让 system_server 把 shell 的权限身份临时借给本应用 UID，
 * 于是本进程发出的 binder 调用被视为持有 shell 权限，从而通过运营商配置的写入校验。
 *
 * 该身份委托自 Android 16 起提供，并要求 Shizuku 服务端版本足够新。
 */
internal object ShellIdentity {

    /** 委托只对这些权限生效，且必须与清单里的声明一致。 */
    val MODEM_PERMISSIONS = arrayOf(
        "android.permission.MODIFY_PHONE_STATE",
        "android.permission.READ_PHONE_STATE"
    )

    /**
     * 在借到的 shell 身份下执行 [block]，无论成败都归还身份。
     * @throws IdentityUnavailable 系统不支持或未真正放行，调用方应转向下一级通道
     */
    fun runAsShell(context: Context, permissions: Array<String>, block: () -> Unit) {
        HiddenAccess.ensure()
        val manager = SystemServices.activityManager()
            ?: throw IdentityUnavailable("无法取得 IActivityManager")

        val start = SystemServices.method(
            manager,
            "startDelegateShellPermissionIdentity",
            Int::class.javaPrimitiveType!!,
            Array<String>::class.java
        ) ?: throw IdentityUnavailable("当前系统不支持 shell 权限委托")
        val stop = SystemServices.method(manager, "stopDelegateShellPermissionIdentity")

        try {
            SystemServices.invoke(start, manager, Process.myUid(), permissions)
        } catch (error: Throwable) {
            throw SystemServices.unwrap(error)
        }

        // 委托调用不报错也可能没生效：Android 17 上部分 ROM 只允许 shell 自己拉起的进程借权。
        // 先自检再写，避免白跑一次注定失败的调用并把误导性错误抛给上层。
        if (!granted(context)) {
            stopIdentity(manager, stop)
            throw IdentityUnavailable("shell 权限委托未生效")
        }

        try {
            block()
        } finally {
            stopIdentity(manager, stop)
        }
    }

    private fun stopIdentity(manager: Any, stop: java.lang.reflect.Method?) {
        runCatching { stop?.let { SystemServices.invoke(it, manager) } }
    }

    private fun granted(context: Context): Boolean =
        context.checkSelfPermission("android.permission.MODIFY_PHONE_STATE") ==
            PackageManager.PERMISSION_GRANTED

    class IdentityUnavailable(message: String) : IllegalStateException(message)
}
