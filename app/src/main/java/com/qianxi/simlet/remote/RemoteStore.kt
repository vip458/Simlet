package com.qianxi.simlet.remote

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * 后台下发内容的本机缓存。
 *
 * 单独占一个 SharedPreferences 文件：`simlet_ui` 是界面偏好，`simlet_plans` 是覆盖计划与开机补写进度，
 * 三边的读写方都不同，混在一起任何一边都会读到别人的无关数据。形状照 ui/UiPrefs.kt。
 *
 * 这里只存服务端**下发**的东西，不产出任何关于用户的记录；清除应用数据或卸载就一起没了。
 * 上报用的匿名设备 ID 不在这一层里，那是统计开关的一部分，跟着开关一起落地。
 */
class RemoteStore private constructor(context: Context) {

    private val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val noticeState = mutableStateOf(readNotice())

    /**
     * 当前该显示的公告。
     *
     * 读数时按「最后一次被后台确认」判过期：后台长期不可达时这条 getter 自己就会变 null，
     * 不需要额外的清理任务。
     */
    val notice: Notice?
        get() = noticeState.value.takeIf {
            HubRules.noticeLive(store.getLong(KEY_CONFIRMED, 0L), System.currentTimeMillis())
        }

    /** 上次拿到的 ETag；304 复验全靠它。 */
    val etag: String? get() = store.getString(KEY_ETAG, null)?.takeIf { it.isNotBlank() }

    val lastAttemptMs: Long get() = store.getLong(KEY_ATTEMPT, 0L)

    val bootHours: Int get() = store.getInt(KEY_BOOT_H, HubRules.DEFAULT_BOOT_HOURS)

    /**
     * 收到 200。
     *
     * 策略里只落地拉取间隔：`rp`（上报间隔）和 `on`（统计开关）现在没有消费方，
     * 存下来只会变成一份没人读的副本——等上报端点落地那步再一起写。
     */
    fun apply(config: HubConfig, newEtag: String?, nowMs: Long) {
        val notice = config.notice
        store.edit().apply {
            putLong(KEY_CONFIRMED, nowMs)
            putLong(KEY_ATTEMPT, nowMs)
            putInt(KEY_BOOT_H, HubRules.hours(config.policy.bootHours).toInt())
            if (newEtag != null) putString(KEY_ETAG, newEtag)
            if (notice == null) {
                remove(KEY_ID)
                remove(KEY_TI)
                remove(KEY_BD)
                remove(KEY_UR)
                remove(KEY_LV)
            } else {
                putInt(KEY_ID, notice.id)
                putString(KEY_TI, notice.title)
                putString(KEY_BD, notice.body)
                putString(KEY_UR, notice.url)
                putString(KEY_LV, notice.level)
            }
        }.apply()
        noticeState.value = notice
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

    private fun readNotice(): Notice? {
        val title = store.getString(KEY_TI, null) ?: return null
        if (title.isBlank()) return null
        return Notice(
            id = store.getInt(KEY_ID, 0),
            title = title,
            body = store.getString(KEY_BD, "").orEmpty(),
            url = store.getString(KEY_UR, "").orEmpty(),
            level = store.getString(KEY_LV, "info").orEmpty()
        )
    }

    companion object {
        private const val PREFS = "simlet_remote"
        private const val KEY_ETAG = "hub_etag"
        private const val KEY_ATTEMPT = "hub_attempt"
        private const val KEY_CONFIRMED = "hub_confirmed"
        private const val KEY_BOOT_H = "hub_boot_h"
        private const val KEY_ID = "notice_id"
        private const val KEY_TI = "notice_title"
        private const val KEY_BD = "notice_body"
        private const val KEY_UR = "notice_url"
        private const val KEY_LV = "notice_level"

        @Volatile
        private var instance: RemoteStore? = null

        /** 进程内一份：每次重组都新建会让公告在切回时闪一下。 */
        fun of(context: Context): RemoteStore = instance ?: synchronized(this) {
            instance ?: RemoteStore(context).also { instance = it }
        }
    }
}
