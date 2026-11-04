package com.qianxi.simlet.remote

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.qianxi.simlet.BuildConfig
import java.security.SecureRandom
import org.json.JSONObject

/**
 * 后台下发内容的本机缓存。
 *
 * 单独占一个 SharedPreferences 文件：`simlet_ui` 是界面偏好，`simlet_plans` 是覆盖计划与开机补写进度，
 * 三边的读写方都不同，混在一起任何一边都会读到别人的无关数据。形状照 ui/UiPrefs.kt。
 *
 * 这里存的是服务端**下发**的东西，加上两个只在本机生成的值：匿名设备 ID 和灰度分桶种子。
 * 清除应用数据或卸载就一起没了——没有第二份身份可以把它捞回来，这也是「换台设备就是另一个人」的口径。
 *
 * 每一份下发内容都记着它是从哪个后台来的：ETag 只对同一个后台作数。真机踩过——本机先指向联调用的
 * 内置服务器、再换成正式域名，两边代次恰好一样，客户端拿旧凭据去问新后台收到一个 304，
 * 于是把测试公告当正式公告显示了下去。换了地址就该重新拿一份，而不是复验旧的那份。
 */
class RemoteStore private constructor(context: Context) {

    private val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 这份缓存现在归属的那个后台地址。改构建参数就等于换了源站。 */
    private val hubOrigin = HubRules.origin(BuildConfig.HUB_BASE_URL)

    private val noticeState = mutableStateOf(readNotice())
    private val releaseState = mutableStateOf(readRelease())
    private val noticesState = mutableStateOf<List<Notice>?>(null)
    private val seenState = mutableStateOf(store.getInt(KEY_SEEN, 0))
    private val statsState = mutableStateOf(store.getBoolean(KEY_STATS, HubRules.DEFAULT_STATS_ON))

    /** 缓存里的东西是不是当前这个后台给的。 */
    private fun owned() = hubOrigin != null && store.getString(KEY_ORIGIN, null) == hubOrigin

    /**
     * 当前该显示的公告。
     *
     * 三道闸：构建时没配后台地址就一律不显示（本机缓存可能是上一个包留下的，
     * fork 和自构建的「不联网、没有后台特性」不能靠缓存没写进来这件事成立）；
     * 再按「最后一次被后台确认」判过期，后台长期不可达时这条 getter 自己就会变 null，
     * 不需要额外的清理任务；最后按公告自带的止期判——这一道完全不碰网络。
     */
    val notice: Notice?
        get() = noticeState.value.takeIf {
            HubRules.enabled(BuildConfig.HUB_BASE_URL) && owned() &&
                HubRules.noticeLive(store.getLong(KEY_CONFIRMED, 0L), System.currentTimeMillis(), it?.expMs ?: 0L)
        }

    /**
     * 历史公告列表。
     *
     * 只在内存里，不落盘：列表页是用户主动点开的，每次进去重新拉一次，命中 304 只花一次握手；
     * 存进 prefs 反而多出一份「和后台第几代对不上」的缓存要管，而它省下的那次请求本来就是免费的。
     *
     * 拉失败时返回 null，界面退回显示 [notice] 那一条——不是报错，也不是空白。
     */
    val notices: List<Notice>?
        get() = noticesState.value?.takeIf {
            HubRules.enabled(BuildConfig.HUB_BASE_URL) && owned()
        }

    /** 列表端点的 ETag。和 bootstrap 的各存各的：两个 URL 的正文不是一回事，串用会收到假的 304。 */
    val noticesEtag: String? get() = store.getString(KEY_NOTICES_ETAG, null)?.takeIf { it.isNotBlank() && owned() }

    fun applyNotices(items: List<Notice>, newEtag: String?) {
        if (newEtag != null) store.edit().putString(KEY_NOTICES_ETAG, newEtag).apply()
        noticesState.value = items
    }

    /**
     * 后台登记的版本，用于「检查更新」和首页的重要更新提示。
     *
     * 存的是后台那种短键名 JSON，而不是拆成一堆 prefs 键：这个对象有十四个字段，
     * 拆开写会让这个文件一半以上都是 putString，而且对不上契约文档。
     *
     * 和公告共用同一道构建时开关与同一个「最后一次被后台确认」的时间来判过期：后台停掉或域名失效之后，
     * 一条「v1.0.3 是重要更新」不能一直挂在首页——那时点「检查更新」走的是 GitHub，
     * 界面会一边催升级一边说已最新，两句都不可信。过了有效期这一行自己消失，
     * 更新检查也随之直接回落 GitHub，不需要额外的清理任务。
     */
    val release: HubRelease?
        get() = releaseState.value.takeIf {
            HubRules.enabled(BuildConfig.HUB_BASE_URL) && owned() &&
                HubRules.noticeLive(store.getLong(KEY_CONFIRMED, 0L), System.currentTimeMillis())
        }

    /**
     * 灰度分桶用的本机种子。
     *
     * 只在本机参与计算，**永不上送**——统计开关关掉之后灰度照样得是准的。
     * 卸载或清除应用数据会换一个种子，也就是重新抽一次签。
     */
    val bucketSeed: String = store.getString(KEY_SEED, null) ?: newSeed().also {
        store.edit().putString(KEY_SEED, it).apply()
    }

    /**
     * 上报用的匿名设备 ID。
     *
     * 和灰度分桶的种子是两样东西，各存各的：种子永不上送，这个 ID 只出现在那一条 POST 里。
     * 混用一个值会让「关掉统计」变得暧昧——关掉以后灰度照样要能算，
     * 那就等于关掉统计并没有真的停掉那个唯一能认出这台设备的值。
     * 卸载或清除应用数据会换一个新 ID，也就是当成一台新设备。
     */
    val deviceId: String = store.getString(KEY_DEV, null) ?: newSeed().also {
        store.edit().putString(KEY_DEV, it).apply()
    }

    /**
     * 用户自己的统计开关，默认开。
     *
     * 这是让 README 里那句「数据只最小化采集」站得住的东西，所以摆在设置页最显眼的那一组里，
     * 而不是塞进关于页。关掉以后这条链路在代码上就没有出口。
     */
    val statsOn: Boolean get() = statsState.value

    fun setStatsOn(on: Boolean) {
        statsState.value = on
        store.edit().putBoolean(KEY_STATS, on).apply()
    }

    /** 这条公告用户自己点看过没有。后台没发、或这条不是当前这个后台给的时候，没有可提醒的东西。 */
    fun noticeUnread(notice: Notice?): Boolean {
        val id = notice?.id ?: return false
        return owned() && id != seenState.value
    }

    /**
     * 点开公告入口：未读点从此消失。
     *
     * 自动弹出不走这里——那是一次提示，不等于人读过了。所以弹完点还在，
     * 直到他自己点开口诀那句「用户查看公告后未读提醒消失」才成立。
     *
     * 记成可观察状态而不是只写 prefs：界面读的是这个值，只落库不触发重组的话，
     * 从公告列表返回首页时那个红点会一直挂到下次冷启动。真机踩过，两台都复现。
     */
    fun markNoticeSeen(id: Int) {
        seenState.value = id
        store.edit().putInt(KEY_SEEN, id).apply()
    }

    /**
     * 取走这条该不该自动弹出：同一条公告只给一次机会。
     *
     * 立刻落库而不是等弹窗关闭再记：杀进程重来的第二次启动如果还能弹，
     * 「只弹一次」就变成「每次冷启动都弹」，那是打扰。
     */
    fun takeNoticePopup(notice: Notice?): Notice? {
        val pending = notice ?: return null
        if (pending.id <= 0 || store.getInt(KEY_POPPED, 0) == pending.id) return null
        store.edit().putInt(KEY_POPPED, pending.id).apply()
        return pending
    }

    /** 上次拿到的 ETag；只对当前这个后台有效，换了地址就当没拿到过。 */
    val etag: String? get() = store.getString(KEY_ETAG, null)?.takeIf { it.isNotBlank() && owned() }

    val lastAttemptMs: Long get() = store.getLong(KEY_ATTEMPT, 0L)

    /** 公告复验间隔（分钟）。后台没答过话时按默认值，和后台的默认值是同一个数。 */
    val revalidateMin: Int get() = HubRules.minutes(store.getInt(KEY_REVALIDATE_M, HubRules.DEFAULT_REVALIDATE_MIN))

    val reportHours: Int get() = store.getInt(KEY_REPORT_H, HubRules.DEFAULT_REPORT_HOURS)

    val lastReportMs: Long get() = store.getLong(KEY_REPORT, 0L)

    /** 后台的熔断位。关掉时本机开关就算开着也不发。 */
    val policyOn: Boolean get() = store.getBoolean(KEY_POLICY_ON, true)

    /**
     * 收到 200。
     *
     * 采集策略三项一起落：`bv` 是公告复验间隔，`rp` 是上报间隔，`on` 是熔断位。
     * 存下来是为了「这一趟不重试」——后台宕了或者被人关了的时候，客户端不该每次启动都再问一遍。
     *
     * 公告为空时连止期一起删掉：留着上一次的止期，下一条没有止期的公告会被它误杀。
     */
    fun apply(config: HubConfig, newEtag: String?, nowMs: Long) {
        val notice = config.notice
        val release = config.release
        // 在赋值之前取旧 id：赋完再比，两边永远是同一个值，这条判断就成了永远不成立的死代码。
        val previousId = noticeState.value?.id
        store.edit().apply {
            putLong(KEY_CONFIRMED, nowMs)
            putLong(KEY_ATTEMPT, nowMs)
            putString(KEY_ORIGIN, hubOrigin.orEmpty())
            putInt(KEY_REVALIDATE_M, HubRules.minutes(config.policy.revalidateMin))
            putInt(KEY_REPORT_H, HubRules.hours(config.policy.reportHours).toInt())
            putBoolean(KEY_POLICY_ON, config.policy.statsOn)
            if (newEtag != null) putString(KEY_ETAG, newEtag)
            if (notice == null) {
                remove(KEY_ID)
                remove(KEY_TI)
                remove(KEY_BD)
                remove(KEY_UR)
                remove(KEY_LV)
                remove(KEY_EXP)
            } else {
                putInt(KEY_ID, notice.id)
                putString(KEY_TI, notice.title)
                putString(KEY_BD, notice.body)
                putString(KEY_UR, notice.url)
                putString(KEY_LV, notice.level)
                putLong(KEY_EXP, notice.expMs)
            }
            if (release == null) {
                remove(KEY_REL)
            } else {
                putString(KEY_REL, release.toJson().toString())
            }
        }.apply()
        noticeState.value = notice
        releaseState.value = release
        // 公告换了一条，内存里的历史列表就不再作数：下一次进列表页重新拉，
        // 而不是把上一代的列表当成最新的显示下去。
        if (previousId != notice?.id) noticesState.value = null
    }

    /**
     * 记一次尝试。304 和被拦下的失败都要调。
     *
     * `confirmed` 只有服务端明确答过话才算：失败时只推时间，不延长缓存的有效期——
     * 否则一次网络抖动就能让公告一直显示下去。
     */
    fun markAttempt(nowMs: Long, confirmed: Boolean) {
        store.edit().apply {
            putLong(KEY_ATTEMPT, nowMs)
            if (confirmed) putLong(KEY_CONFIRMED, nowMs)
        }.apply()
    }

    /**
     * 记一次上报尝试。
     *
     * 和 markAttempt 一样是「先记再发」：两个 Activity 实例挤在一起时，
     * 写在后面挡不住重复发。传 0 是把这条撤回——发失败了得让下次启动有机会再试。
     */
    fun markReport(nowMs: Long) {
        store.edit().putLong(KEY_REPORT, nowMs).apply()
    }

    private fun readRelease(): HubRelease? {
        val raw = store.getString(KEY_REL, null) ?: return null
        // 缓存坏了（版本升级改过字段、或者被人手改过 prefs）就当没有，别让一次读取把首页搞崩。
        return runCatching { parseRelease(JSONObject(raw)) }.getOrNull()
    }

    private fun readNotice(): Notice? {
        val title = store.getString(KEY_TI, null) ?: return null
        if (title.isBlank()) return null
        return Notice(
            id = store.getInt(KEY_ID, 0),
            title = title,
            body = store.getString(KEY_BD, "").orEmpty(),
            url = store.getString(KEY_UR, "").orEmpty(),
            level = store.getString(KEY_LV, "info").orEmpty(),
            expMs = store.getLong(KEY_EXP, 0L)
        )
    }

    companion object {
        private const val PREFS = "simlet_remote"
        private const val KEY_ORIGIN = "hub_origin"
        private const val KEY_ETAG = "hub_etag"
        private const val KEY_NOTICES_ETAG = "hub_notices_etag"
        private const val KEY_ATTEMPT = "hub_attempt"
        private const val KEY_CONFIRMED = "hub_confirmed"
        private const val KEY_REVALIDATE_M = "hub_boot_min"
        private const val KEY_REPORT = "hub_report"
        private const val KEY_REPORT_H = "hub_report_h"
        private const val KEY_POLICY_ON = "hub_policy_on"
        private const val KEY_STATS = "hub_stats_on"
        private const val KEY_DEV = "hub_dev"
        private const val KEY_SEED = "hub_seed"
        private const val KEY_REL = "hub_release"
        private const val KEY_SEEN = "notice_seen_id"
        private const val KEY_POPPED = "notice_popped_id"
        private const val KEY_ID = "notice_id"
        private const val KEY_TI = "notice_title"
        private const val KEY_BD = "notice_body"
        private const val KEY_UR = "notice_url"
        private const val KEY_LV = "notice_level"
        private const val KEY_EXP = "notice_exp_ms"

        /** 16 字节随机数写成 32 位十六进制：和将来上报用的匿名设备 ID 同一个形状，但两者互不相干。 */
        private fun newSeed(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        @Volatile
        private var instance: RemoteStore? = null

        /** 进程内一份：每次重组都新建会让公告在切回时闪一下。 */
        fun of(context: Context): RemoteStore = instance ?: synchronized(this) {
            instance ?: RemoteStore(context).also { instance = it }
        }
    }
}
