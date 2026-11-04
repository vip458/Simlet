package com.qianxi.simlet

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import com.qianxi.simlet.remote.HubClient
import com.qianxi.simlet.remote.HubOutcome
import com.qianxi.simlet.remote.HubRules
import com.qianxi.simlet.remote.RemoteStore
import com.qianxi.simlet.ui.UiPrefs
import com.qianxi.simlet.ui.screen.AboutScreen
import com.qianxi.simlet.ui.screen.HomeScreen
import com.qianxi.simlet.ui.screen.LicenseScreen
import com.qianxi.simlet.ui.screen.NoticesScreen
import com.qianxi.simlet.ui.screen.PrivacyScreen
import com.qianxi.simlet.ui.screen.SettingsScreen
import com.qianxi.simlet.ui.screen.SetupScreen
import com.qianxi.simlet.ui.screen.SetupStage
import com.qianxi.simlet.ui.screen.StatsScreen
import com.qianxi.simlet.ui.screen.TermsScreen
import com.qianxi.simlet.ui.screen.resolveSetupStage
import com.qianxi.simlet.ui.theme.SimletTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import rikka.shizuku.Shizuku

/**
 * 页面路由。首页与环境自检不进栈，由 Shizuku 授权状态决定。
 *
 * `level` 是层级，切屏动画用它判断该往哪边推：数字大的在里侧，
 * 进它就从右滑入、退它就从左滑回；根层两页同层，没有前后关系，只淡入淡出。
 */
private enum class Page(val level: Int) {
    Setup(0), Home(0), Settings(1), Notices(1), About(2), Licenses(3), Privacy(3), Terms(3), Stats(3)
}

/** 公告这条链路和 remote/HubClient 用同一个日志标签，抓 logcat 时一条命令能看全。 */
private const val HUB_TAG = "SimletHub"

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
                        Page.Privacy -> PrivacyScreen(onBack = ::pop)
                        Page.Terms -> TermsScreen(onBack = ::pop)
                        Page.Stats -> {
                            val store = RemoteStore.of(this@SimletActivity)
                            StatsScreen(
                                onBack = ::pop,
                                statsOn = store.statsOn,
                                onToggle = store::setStatsOn
                            )
                        }

                        Page.About -> AboutScreen(
                            onBack = ::pop,
                            onOpenLicenses = { push(Page.Licenses) },
                            onOpenPrivacy = { push(Page.Privacy) },
                            onOpenTerms = { push(Page.Terms) },
                            onOpenStats = { push(Page.Stats) }
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

                        Page.Home -> HomeScreen(
                            onOpenSettings = { push(Page.Settings) },
                            onOpenNotices = { push(Page.Notices) }
                        )

                        Page.Notices -> NoticesScreen(onBack = ::pop)
                        Page.Setup -> SetupScreen(
                            stage = stage,
                            onRequestPermission = ::askPermission,
                            onOpenNotices = { push(Page.Notices) },
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
        // 「进首页就校验」落在这里：onResume 是每次回到前台都会走的一次，而 onCreate 只在冷启动走。
        // 公告下线要快，靠的就是这一次；它发的是带 ETag 的条件请求，命中 304 只花一次握手。
        // 这里不再加「本实例试过没有」的开关：闸门在 prefs 的时间戳上，跨实例也作数，
        // 而实例级开关恰恰挡不住它想挡的那种情况（系统连续重建两个实例，两边都是新实例）。
        syncHubOnce()
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

    /**
     * 启动时和后台同步一次：先取配置（公告、更新信息、采集策略），再决定这条启动要不要上报。
     *
     * 两件事共用一个协程是有意的：上报要按「刚拿到的策略」放行，分成两个并发的活会让第一条上报
     * 带着上一个版本的间隔设置发出去。
     *
     * 整条链路可以完全静默，因为三件事都成立：没配后台地址（`HUB_BASE_URL` 留空，fork 和自构建的默认）
     * 时一个请求都不发；拉到才写缓存、首页那一组才出现；拉不到连原因都不上屏——
     * 用户此刻要做的是改 SIM 信息，不是一个「检查失败」的提示。
     *
     * 时间闸门在 HubRules 里，用「上次尝试」而不是「上次成功」：后台宕了的时候，
     * 每次启动都重试只会让弱网用户多白等一个连接超时。
     */
    private fun syncHubOnce() {
        if (!HubRules.enabled(BuildConfig.HUB_BASE_URL)) return
        val app = applicationContext
        ioScope.launch {
            val store = RemoteStore.of(app)
            val started = System.currentTimeMillis()
            // 语言取法和设置页那次一致：应用内设置优先，没设置才看系统语言。
            val tag = UiPrefs.of(app).localeTag ?: Locale.getDefault().language
            val lang = HubRules.queryLang(tag)
            if (HubRules.shouldRevalidate(store.lastAttemptMs, started, store.revalidateMin)) {
                fetchConfig(store, started, lang)
            } else {
                Log.d(HUB_TAG, "跳过这趟：距上次尝试不足 ${store.revalidateMin} 分钟")
            }
            reportBoot(store, lang)
        }
    }

    private suspend fun fetchConfig(store: RemoteStore, started: Long, lang: String) {
        // 先把「我试过了」写下去，再发请求。
        // 真机踩过：安装后第一次启动时系统紧接着又重建了一次 Activity，两个实例都读到
        // 「从没试过」，各发一趟。写在请求之后挡不住这个窗口，写在前面最多是白记一次。
        store.markAttempt(started, confirmed = false)
        val outcome = HubClient.fetch(
            currentEtag = store.etag,
            lang = lang,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE
        )
        val at = System.currentTimeMillis()
        withContext(Dispatchers.Main) {
            when (outcome) {
                is HubOutcome.Updated -> {
                    store.apply(outcome.config, outcome.etag, at)
                    Log.i(HUB_TAG, "公告已更新 id=${outcome.config.notice?.id ?: -1}")
                }

                HubOutcome.Cached -> store.markAttempt(at, confirmed = true)
                HubOutcome.Unreachable -> Unit
            }
        }
    }

    /**
     * 每天一条启动上报。
     *
     * 机型和系统版本是这条链路上唯一会上去的设备信息，加上一个只在本机生成的匿名 ID。
     * 发失败就把时间戳退回 0，让下次启动再试一次——一次网络抖动不该让这台设备整天从统计里消失。
     */
    private fun reportBoot(store: RemoteStore, lang: String) {
        val now = System.currentTimeMillis()
        // 两个挡住的原因分开记：用户报「我关了统计它还在发」时，这一行日志能直接分清是开关没生效
        // 还是只是没到间隔——隔着反馈猜是猜不出来的。
        if (!store.statsOn) {
            Log.d(HUB_TAG, "统计开关已关，不发上报")
            return
        }
        if (!HubRules.reportWanted(true, store.policyOn, store.lastReportMs, now, store.reportHours)) {
            Log.d(HUB_TAG, "这条上报不发：后台熔断或距上次上报不足 ${store.reportHours} 小时")
            return
        }
        store.markReport(now)
        if (!HubClient.report(
                deviceId = store.deviceId,
                brand = Build.MANUFACTURER,
                model = Build.MODEL,
                sdk = Build.VERSION.SDK_INT,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                lang = lang,
                event = "boot"
            )
        ) {
            store.markReport(0L)
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
