package com.qianxi.simlet.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.io.InputStream

/** 通道三要的服务端档位：API 13 且小版本 6 起，也就是 13.6。见 [delegateReady]。 */
private const val MIN_DELEGATE_API = 13
private const val MIN_DELEGATE_PATCH = 6

/**
 * Shizuku 接入面：连接状态、授权、以 shell 身份执行命令、代理系统服务 binder。
 */
object ShizukuLink {

    private val PACKAGES = listOf("moe.shizuku.privileged.api", "moe.shizuku.manager")

    /**
     * 用 [Shizuku.pingBinder] 而不是直接取缓存的 binder：
     * 后台写入进程是全新进程，客户端要到这一刻才去解析 binder，读缓存会一直判「未就绪」。
     */
    fun binderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun granted(): Boolean = binderAlive() &&
        runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
            .getOrDefault(false)

    /**
     * 通道三靠「以 shell 身份代理 binder 调用」，这条路要的服务端档位是 13.6 起。
     *
     * 档位是两个数一起报的：`getVersion()` 给 API 号，`getServerPatchVersion()` 给小版本，
     * 中间没有「13.6.0 = 130600」那种拼法。红米实测（Shizuku-Next 14.0.31，adb 启动）：
     * `api=13 patch=6 uid=2000`——分支版把应用版本号升到 14，报给客户端的仍然是 13/6。
     *
     * 原来这里写的是 `getVersion() >= 130600`，也就是拿 13 去比 130600：永远不成立，
     * 于是自检页那一行在任何机器上都是灰点。那不是「这台不支持」，是我们把档位读错了。
     *
     * 这两个数由服务端 attach 的回包填进来，连上之前各是 -1，所以那时候判为不支持；
     * 界面给了一次重读（见 SetupScreen），别拿冷启动那一瞬的灰点去劝人升级 Shizuku。
     */
    fun delegateCapable(): Boolean = delegateReady(
        api = runCatching { Shizuku.getVersion() }.getOrDefault(-1),
        patch = runCatching { Shizuku.getServerPatchVersion() }.getOrDefault(-1)
    )

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
        // 进程退出后要把管道读空再返回。dumpsys isub 在部分 ROM 上是几十 KB，
        // 原来这里只给读线程 3 秒收尾，超时就拿着截断的文本继续往下解析——
        // 排在后面的卡槽行一丢，直接表现成「识别不到卡」。
        readers.forEach { it.join(15_000L) }
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

/**
 * 13.6 起才有走通道三的那条委托路。两个数分开比，谁大谁小一目了然：
 * 13/5 不算，13/6 算，14/0 也算，读不到（-1）一律不算支持。
 *
 * 拆成纯函数是为了让 JVM 单测能钉住它——上一版就是因为它长在 `Shizuku` 调用里，
 * 没法测，才把 13 和 130600 放在一起比了那么久。
 */
internal fun delegateReady(api: Int, patch: Int): Boolean =
    api > MIN_DELEGATE_API || (api == MIN_DELEGATE_API && patch >= MIN_DELEGATE_PATCH)
