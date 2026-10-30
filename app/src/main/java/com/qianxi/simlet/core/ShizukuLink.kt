package com.qianxi.simlet.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.io.InputStream

/**
 * Shizuku 接入面：连接状态、授权、以 shell 身份执行命令、代理系统服务 binder。
 */
object ShizukuLink {

    private val PACKAGES = listOf("moe.shizuku.privileged.api", "moe.shizuku.manager")

    const val MIN_DELEGATE_VERSION = 130600

    /**
     * 用 [Shizuku.pingBinder] 而不是直接取缓存的 binder：
     * 后台写入进程是全新进程，客户端要到这一刻才去解析 binder，读缓存会一直判「未就绪」。
     */
    fun binderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun granted(): Boolean = binderAlive() &&
        runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
            .getOrDefault(false)

    /** 委托 shell 权限身份的能力由服务端提供，版本过低时第三级兜底也走不通。 */
    fun delegateCapable(): Boolean = runCatching { Shizuku.getVersion() >= MIN_DELEGATE_VERSION }.getOrDefault(false)

    fun serverVersion(): Int = runCatching { Shizuku.getVersion() }.getOrDefault(0)

    fun askPermission(requestCode: Int = 0): Boolean {
        if (!binderAlive()) return false
        return runCatching { Shizuku.requestPermission(requestCode); true }.getOrDefault(false)
    }

    fun installed(context: Context): Boolean =
        PACKAGES.any { context.packageManager.getLaunchIntentForPackage(it) != null }

    fun launchHost(context: Context): Boolean {
        for (pkg in PACKAGES) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) return true
        }
        return false
    }

    /** 把系统服务 binder 包装成「以 shell 身份调用」的代理。 */
    fun asShell(binder: IBinder): IBinder = ShizukuBinderWrapper(binder)

    /** 注册 binder 到达监听（粘性：已就绪时立即回调），返回句柄供后续注销。 */
    fun addBinderListener(onAvailable: () -> Unit): Shizuku.OnBinderReceivedListener {
        val listener = Shizuku.OnBinderReceivedListener { onAvailable() }
        runCatching { Shizuku.addBinderReceivedListenerSticky(listener) }
        return listener
    }

    fun removeBinderListener(listener: Shizuku.OnBinderReceivedListener) {
        runCatching { Shizuku.removeBinderReceivedListener(listener) }
    }

    /** 以 shell 身份跑一条命令并收集 stdout / stderr。 */
    fun exec(vararg command: String): ShellOutput {
        val process = newShellProcess(arrayOf("sh", "-c", command.joinToString(" ")))
        val out = StringBuilder()
        val err = StringBuilder()
        val readers = listOf(
            consume(process.inputStream, out),
            consume(process.errorStream, err)
        )
        val code = process.waitFor()
        readers.forEach { it.join(3_000L) }
        return ShellOutput(code, out.toString(), err.toString())
    }

    private fun newShellProcess(command: Array<String>): Process {
        val factory = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        factory.isAccessible = true
        return factory.invoke(null, command, null, null) as Process
    }

    private fun consume(stream: InputStream, sink: StringBuilder): Thread = Thread {
        runCatching { stream.bufferedReader().use { reader -> sink.append(reader.readText()) } }
    }.apply { isDaemon = true; start() }

    data class ShellOutput(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = exitCode == 0
        val merged: String get() = listOf(stdout, stderr).filter(String::isNotBlank).joinToString("\n")
    }
}
