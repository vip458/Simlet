package com.qianxi.simlet.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.qianxi.simlet.ui.theme.NightMode

/**
 * 界面偏好。
 *
 * 单独占一个 SharedPreferences 文件：core 的 `simlet_plans` 存的是覆盖计划与开机补写进度，
 * 把 UI 开关混进去会让那边的读写读到无关数据。
 */
class UiPrefs private constructor(context: Context) {

    private val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // 用 backing state 而不是 var + private set：后者的私有 setter 会和下面的
    // setNightMode 生成同样的 JVM 签名，编译期直接冲突。
    private val nightModeState = mutableStateOf(readNightMode())
    private val localeState = mutableStateOf(store.getString(KEY_LOCALE, null))

    val nightMode: NightMode get() = nightModeState.value

    /** 界面语言标签；null 表示跟随系统。 */
    val localeTag: String? get() = localeState.value

    fun setLocaleTag(tag: String?) {
        store.edit().apply {
            if (tag == null) remove(KEY_LOCALE) else putString(KEY_LOCALE, tag)
        }.apply()
        localeState.value = tag
    }

    fun setNightMode(mode: NightMode) {
        store.edit().putString(KEY_NIGHT, mode.name).apply()
        nightModeState.value = mode
    }

    private fun readNightMode(): NightMode =
        store.getString(KEY_NIGHT, null)?.let { saved ->
            NightMode.entries.firstOrNull { it.name == saved }
        } ?: NightMode.FollowSystem

    companion object {
        private const val PREFS = "simlet_ui"
        private const val KEY_NIGHT = "night_mode"
        private const val KEY_LOCALE = "locale_tag"

        @Volatile
        private var instance: UiPrefs? = null

        /** 进程内一份：每次重组都新建会让开关在切回时回弹。 */
        fun of(context: Context): UiPrefs = instance ?: synchronized(this) {
            instance ?: UiPrefs(context).also { instance = it }
        }
    }
}
