package com.qianxi.simlet

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.Lifecycle
import com.qianxi.simlet.core.AppLog
import com.qianxi.simlet.core.HiddenAccess
import com.qianxi.simlet.core.PlanStore
import com.qianxi.simlet.core.ShizukuLink
import com.qianxi.simlet.remote.HubClient
import com.qianxi.simlet.remote.HubOutcome
import com.qianxi.simlet.remote.HubRules
import com.qianxi.simlet.remote.Notice
import com.qianxi.simlet.remote.NoticeNotifier
import com.qianxi.simlet.remote.RemoteStore
import com.qianxi.simlet.ui.UiPrefs
import com.qianxi.simlet.ui.component.NoticeDialog
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
    private var askedNotifications = false

    /** 上一次发授权请求的时刻（0 = 没有在等的结果）和它是谁点的：用来分清「人拒了」和「窗没弹出来」。 */
    private var permissionAskedAt = 0L
    private var permissionAskedManually = false

    /** 从通知栏点进来要摆的那一条。放在 Activity 而不是页面里：点通知时人在哪一屏都可能。 */
    private var noticeFromBar by mutableStateOf<Notice?>(null)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 通知权限这一问不给结果加反应。
     *
     * 拒了不做任何提示：公告照样在首页那一行、照样有红点、照样进列表，通知栏只是多出来的那条通道。
     * 为一个「少一种提醒方式」弹一段解释，比没有提醒更扰人。
     */
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * 授权结果的两句话分开说。
     *
     * 一秒之内回到手的结果不可能是人做的选择——那只说明 Shizuku 的授权窗压根没弹出来
     * （小米这类系统把「后台弹出界面」关掉就是这个下场）。那时候讲「未授予权限」是把系统拦窗
     * 说成人的拒绝，人听完只会再去点一遍按钮，原地打转。
     *
     * 自动发起的那一次（打开 App 就发）不吭声：窗没弹出来不是用户的错，
     * 而且每次冷启动都顶一句「去改系统设置」比不提示更扰人。
     */
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, result ->
        runOnUiThread {
            syncState()
            val asked = permissionAskedAt > 0L
            val waited = SystemClock.elapsedRealtime() - permissionAskedAt
            permissionAskedAt = 0L
            if (result != PackageManager.PERMISSION_GRANTED && asked) {
                when {
                    waited >= NO_DIALOG_MS ->
                        Toast.makeText(this, R.string.toast_permission_denied, Toast.LENGTH_LONG).show()

                    permissionAskedManually -> {
                        // 刚连上那 3 秒里服务端可能还没把授权记录读出来，这一眼的拒绝不是人的选择，
                        // 也不是弹窗被系统挡；说错方向会让人去翻根本没问题的那个开关。
                        val reason = if (ShizukuLink.attachedRecently()) {
                            R.string.toast_permission_warming
                        } else {
                            R.string.toast_permission_no_dialog
                        }
                        Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            ShizukuLink.markAttached()
            syncState()
            askAutomatically()
        }
    }

    /**
     * 服务死了要清掉「这一趟已经自动请求过」这一笔：
     * 不清的话，服务回来后界面只会显示「需要授权」，而那条本来该自动补发的请求永远发不出去，
     * 人只能自己去点一次按钮。
     */
    private val binderDead = Shizuku.OnBinderDeadListener {
        runOnUiThread {
            askedAutomatically = false
            syncState()
        }
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
        showNoticeFromIntent(intent)

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
                            onRecheck = ::recheck
                        )
                    }
                }
                // 通知栏那条点进来的弹窗摆在这一层：它不属于任何一屏，人在子页时点通知也得能看见。
                NoticeDialog(
                    visible = noticeFromBar != null,
                    notice = noticeFromBar,
                    onDismiss = { noticeFromBar = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showNoticeFromIntent(intent)
    }

    /**
     * 从通知栏点进来：直接把这一条的居中弹窗摆出来，不停在首页让人再找一遍。
     *
     * 顺手清两样账：通知本身（autoCancel 已经收了，但本机记的「挂着哪条」要一起抹掉，
     * 不然下次比对会去撤一条早就不在的），和那个红点——他人已经在这条公告面前了。
     *
     * 这条已经不生效（换过了、下线了、本机判过期了）就不编一个弹窗出来，正常落到界面。
     *
     * 凭据对不上也一样不弹——那种 intent 不是通知栏按的，是本机别的应用自己拼出来的。
     */
    private fun showNoticeFromIntent(intent: Intent?) {
        val id = intent?.getIntExtra(EXTRA_NOTICE_ID, 0) ?: return
        if (id <= 0) return
        val token = intent.getStringExtra(EXTRA_NOTICE_TOKEN).orEmpty()
        // 取走就抹掉：转屏重建 Activity 时读的还是同一个 Intent，不抹会再弹一次。
        intent.removeExtra(EXTRA_NOTICE_ID)
        intent.removeExtra(EXTRA_NOTICE_TOKEN)
        val store = RemoteStore.of(this)
        // 只认发通知那次写进本机缓存的那一个数，且用完即废。
        // 首页 Activity 是导出的（桌面图标要用），本机任何应用都能 startActivity
        // 并塞一个公告 id 进来，替后台把弹窗按到用户脸上；id 本身没有秘密，只有这个随机数是。
        if (token.isEmpty() || token != store.noticeTapToken) return
        NoticeNotifier.dismiss(this, store)
        val notice = store.notice?.takeIf { it.id == id } ?: return
        store.markNoticeSeen(id)
        noticeFromBar = notice
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
        askNotificationsOnce()
    }

    /**
     * 通知权限只在「真有一条公告可提醒、且人正停在首页」时问一次。
     *
     * 不在启动时问：没有公告的设备不该为一个用不上的通道看到系统弹窗。
     * 不在子页或后台问：那会把系统弹窗排到下一次前台，点进来的人莫名其妙。
     * 拒了也不追第二次：`askedNotifications` 是实例级的，重装进程才重置——
     * Android 13 之后第二次点「不允许」就是永久拒绝，再 launch 只会白白挡一次首页。
     *
     * 这一问只看运行时权限，不看通知总闸：总闸被人关过不等于没给过权限，
     * 为一个系统已经答过「允许」的东西再弹一次窗，是把设置页里的一次调整当成拒绝。
     */
    private fun askNotificationsOnce() {
        if (askedNotifications || !HubRules.enabled(BuildConfig.HUB_BASE_URL)) return
        if (NoticeNotifier.granted(this)) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || stack.isNotEmpty() || !ready) return
        if (RemoteStore.of(this).notice == null) return
        askedNotifications = true
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun syncState() {
        ready = ShizukuLink.granted()
        if (ready) {
            reapplyOncePerBoot()
        } else {
            stage = resolveSetupStage(this)
        }
    }

    /**
     * 这一页的「再看一眼」：重读状态，必要时补发一次授权请求。
     *
     * 这里做不了更多：binder 只能由服务端推过来，客户端没有回源的门路（见 ShizukuLink 里那段注释），
     * 所以能做的就是把「要人退出重进才知道」换成「这一页自己每秒看一眼」。
     */
    private fun recheck() {
        syncState()
        askAutomatically()
    }

    private fun askPermission() {
        if (!ShizukuLink.binderAlive()) {
            Toast.makeText(this, R.string.toast_shizuku_first, Toast.LENGTH_LONG).show()
            syncState()
            return
        }
        permissionAskedManually = true
        permissionAskedAt = SystemClock.elapsedRealtime()
        ShizukuLink.askPermission()
        syncState()
    }

    private fun askAutomatically() {
        if (askedAutomatically || ready) return
        if (ShizukuLink.binderAlive()) {
            askedAutomatically = true
            permissionAskedManually = false
            permissionAskedAt = SystemClock.elapsedRealtime()
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
                AppLog.d(HUB_TAG, "跳过这趟：距上次尝试不足 ${store.revalidateMin} 分钟")
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
                    AppLog.i(HUB_TAG, "公告已更新 id=${outcome.config.notice?.id ?: -1}")
                }

                HubOutcome.Cached -> store.markAttempt(at, confirmed = true)
                HubOutcome.Unreachable -> Unit
            }
            syncNoticeBar(store)
        }
    }

    /**
     * 让通知栏跟着公告走：该挂的挂上，不该留的撤掉。
     *
     * 挂的前提是「这条落地时人没在首页」。亮着首页的那一次已经被居中弹窗说过了，同一个公告
     * 只有一个「主动出声」的名额（RemoteStore.takeNoticeAttention），两处都响就是同一句话讲两遍。
     * 名额没被弹窗拿走的情况：切去后台了、或者停在子页——这两种恰恰是公告没人看见的时刻。
     *
     * 撤的判据是「挂着的这条已经不是当前生效的那条」：后台下线、换了一条、本机按止期判过期，
     * 三种都算。公告早就不播了而通知还挂在人下拉列表里，是发出去收不回来的那种难看。
     * 这里三条分支都要走（包括没连上的那条），因为「本机判过期」不需要网络。
     */
    private fun syncNoticeBar(store: RemoteStore) {
        val live = store.notice
        if (store.noticeNotifiedId > 0 && store.noticeNotifiedId != live?.id) NoticeNotifier.dismiss(this, store)
        if (live == null) return
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && stack.isEmpty() && ready) return
        // 没有许可就不占名额：占了这个弹窗也不会补上，那条公告等于什么提示都没有过。
        if (!NoticeNotifier.allowed(this)) return
        if (store.takeNoticeAttention(live) != null) {
            NoticeNotifier.show(this, store, live)
            AppLog.i(HUB_TAG, "公告已挂进通知栏 id=${live.id}")
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
            AppLog.d(HUB_TAG, "统计开关已关，不发上报")
            return
        }
        if (!HubRules.reportWanted(true, store.policyOn, store.lastReportMs, now, store.reportHours)) {
            AppLog.d(HUB_TAG, "这条上报不发：后台熔断或距上次上报不足 ${store.reportHours} 小时")
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

        /** 通知栏带进来的那一条公告。给 NoticeNotifier 和这里读同一个键，别各写一个字符串。 */
        const val EXTRA_NOTICE_ID = "simlet.notice_id"

        /** 随公告 id 一起的一次性凭据，由 [com.qianxi.simlet.remote.NoticeNotifier.show] 生成。 */
        const val EXTRA_NOTICE_TOKEN = "simlet.notice_token"

        /** 授权结果快过这个时刻就当「窗没弹出来」：人读一句话再点一下不可能短于一秒。 */
        private const val NO_DIALOG_MS = 1_000L
    }
}
