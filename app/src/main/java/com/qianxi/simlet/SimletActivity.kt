package com.qianxi.simlet

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qianxi.simlet.core.HiddenAccess
import com.qianxi.simlet.core.PlanStore
import com.qianxi.simlet.core.ShizukuLink
import com.qianxi.simlet.ui.UiPrefs
import com.qianxi.simlet.ui.screen.AboutScreen
import com.qianxi.simlet.ui.screen.HomeScreen
import com.qianxi.simlet.ui.screen.LicenseScreen
import com.qianxi.simlet.ui.screen.SettingsScreen
import com.qianxi.simlet.ui.screen.SetupScreen
import com.qianxi.simlet.ui.screen.SetupStage
import com.qianxi.simlet.ui.screen.resolveSetupStage
import com.qianxi.simlet.ui.theme.SimletTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import rikka.shizuku.Shizuku

/**
 * 页面路由。首页与环境自检不进栈，由 Shizuku 授权状态决定。
 *
 * `level` 是层级，切屏动画用它判断该往哪边推：数字大的在里侧，
 * 进它就从右滑入、退它就从左滑回；根层两页同层，没有前后关系，只淡入淡出。
 */
private enum class Page(val level: Int) {
    Setup(0), Home(0), Settings(1), About(2), Licenses(3)
}

/**
 * 切屏动画：Material 的轴向共享（shared axis X）。
 *
 * 进子页时新页从右侧 1/4 屏处推入、旧页向左退场，返回时方向相反——
 * 只淡入淡出看不出层级，整屏左右滑又太重，1/4 屏是 Material 给这个场景的档位。
 * 同层的自检 ↔ 首页没有前后关系（授权状态一变就换），只淡。
 */
private fun AnimatedContentTransitionScope<Page>.screenTransition(): ContentTransform {
    if (targetState.level == initialState.level) {
        return fadeIn(tween(200)) togetherWith fadeOut(tween(160))
    }
    val edge = if (targetState.level > initialState.level) 1 else -1
    val enter = slideInHorizontally(tween(260)) { width -> edge * width / 4 } + fadeIn(tween(200))
    val exit = slideOutHorizontally(tween(260)) { width -> -edge * width / 4 } + fadeOut(tween(200))
    return enter togetherWith exit
}

class SimletActivity : ComponentActivity() {

    private var ready by mutableStateOf(false)
    private var stage by mutableStateOf(SetupStage.PERMISSION_PENDING)

    /**
     * 子页返回栈；首页与环境自检由 Shizuku 是否就绪决定，不入栈。
     *
     * 显式存进实例状态而不是靠 Activity 自动恢复：切语言会 recreate，
     * 不存的话人在设置页点了语言就被弹回首页。
     */
    private val stack = mutableStateListOf<Page>()

    private var askedAutomatically = false
    private var reapplyStarted = false
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, result ->
        runOnUiThread {
            syncState()
            if (result != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            syncState()
            askAutomatically()
        }
    }

    private val binderDead = Shizuku.OnBinderDeadListener {
        runOnUiThread { syncState() }
    }

    override fun attachBaseContext(newBase: Context) {
        // 不用 AppCompat 的 setApplicationLocales：那要求 AppCompatActivity + AppCompat 主题，
        // 会牵动窗口主题；手动覆盖配置对所有 API 级别是同一条路径。
        val tag = UiPrefs.of(newBase).localeTag
        val wrapped = tag?.let { newBase.wrapLocale(it) } ?: newBase
        super.attachBaseContext(wrapped)
    }

    private fun Context.wrapLocale(tag: String): Context {
        val config = Configuration(resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return createConfigurationContext(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        HiddenAccess.ensure()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        savedInstanceState?.getStringArray(EXTRA_ROUTES)?.forEach { name ->
            Page.entries.firstOrNull { it.name == name }?.let(stack::add)
        }

        Shizuku.addRequestPermissionResultListener(permissionResult)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)

        syncState()
        askAutomatically()

        setContent {
            SimletTheme {
                BackHandler(enabled = stack.isNotEmpty()) { pop() }
                AnimatedContent(
                    targetState = stack.lastOrNull() ?: if (ready) Page.Home else Page.Setup,
                    transitionSpec = { screenTransition() },
                    label = "screen"
                ) { page ->
                    when (page) {
                        Page.Licenses -> LicenseScreen(onBack = ::pop)
                        Page.About -> AboutScreen(
                            onBack = ::pop,
                            onOpenLicenses = { push(Page.Licenses) }
                        )

                        Page.Settings -> SettingsScreen(
                            onBack = ::pop,
                            onOpenAbout = { push(Page.About) },
                            onLanguageChange = { tag ->
                                // 这里的 this 是 AnimatedContent 的 scope，得点名 Activity
                                UiPrefs.of(this@SimletActivity).setLocaleTag(tag)
                                recreate()
                            }
                        )

                        Page.Home -> HomeScreen(onOpenSettings = { push(Page.Settings) })
                        Page.Setup -> SetupScreen(
                            stage = stage,
                            onRequestPermission = ::askPermission,
                            onRecheck = {
                                syncState()
                                askAutomatically()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun push(route: Page) {
        if (stack.lastOrNull() != route) stack.add(route)
    }

    private fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    override fun onResume() {
        super.onResume()
        syncState()
    }

    private fun syncState() {
        ready = ShizukuLink.granted()
        if (ready) {
            reapplyOncePerBoot()
        } else {
            stage = resolveSetupStage(this)
        }
    }

    private fun askPermission() {
        if (!ShizukuLink.binderAlive()) {
            Toast.makeText(this, R.string.toast_shizuku_first, Toast.LENGTH_LONG).show()
            syncState()
            return
        }
        ShizukuLink.askPermission()
        syncState()
    }

    private fun askAutomatically() {
        if (askedAutomatically || ready) return
        if (ShizukuLink.binderAlive()) {
            askedAutomatically = true
            ShizukuLink.askPermission()
        }
    }

    /** 新一次开机时把已保存的覆盖补写回去；同一开机周期只做一次。 */
    private fun reapplyOncePerBoot() {
        if (reapplyStarted) return
        reapplyStarted = true
        val app = applicationContext
        ioScope.launch {
            runCatching { PlanStore.reapplyAfterBoot(app) }
                .onFailure { reapplyStarted = false }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArray(EXTRA_ROUTES, stack.map(Page::name).toTypedArray())
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ROUTES = "simlet.routes"
    }
}
