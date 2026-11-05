package com.qianxi.simlet.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qianxi.simlet.BuildConfig
import com.qianxi.simlet.remote.HubClient
import com.qianxi.simlet.remote.HubOutcome
import com.qianxi.simlet.remote.HubRelease
import com.qianxi.simlet.remote.HubRules
import com.qianxi.simlet.remote.RemoteStore
import com.qianxi.simlet.ui.UiPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** 更新模块的界面状态；每种结果都是一种状态，而不是把文案拼进字符串。 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data object UpToDate : UpdateState
    data object NoRelease : UpdateState
    data object RateLimited : UpdateState
    data class Failed(val reason: String, val whileDownloading: Boolean = false) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class ReadyToInstall(val file: File, val release: ReleaseInfo) : UpdateState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun check() {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) { checkNow() }.asState()
        }
    }

    /**
     * 后台优先，问不到才回落 GitHub。
     *
     * 老的那条 GitHub 通道一行没删：后台挂了、域名废了、fork 把地址留空，
     * 用户点「检查更新」都还得有结果。
     */
    private fun checkNow(): CheckOutcome = checkFromHub() ?: GitHubClient.fetchLatest(BuildConfig.VERSION_NAME)

    /**
     * 按后台登记的版本判这次该说什么。
     *
     * 返回 null 的含义是「后台在这件事上没给出结论」——没配地址、连不上、没有上线的发布，
     * 这三种都必须继续去问 GitHub。「后台答了但你这台不该提示」不是 null，那是 UpToDate。
     *
     * 手动检查带 ETag：命中 304 只有几十字节，而正文只随后台改动变化，
     * 复验通过就说明本地缓存的那份就是最新的。
     */
    private fun checkFromHub(): CheckOutcome? {
        if (!HubRules.enabled(BuildConfig.HUB_BASE_URL)) return null
        val app = getApplication<Application>()
        val store = RemoteStore.of(app)
        // 语言取法和启动那次一致：应用内设置优先，没设置才看系统语言。
        val tag = UiPrefs.of(app).localeTag ?: Locale.getDefault().language
        val outcome = HubClient.fetch(
            currentEtag = store.etag,
            lang = HubRules.queryLang(tag),
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE
        )
        val at = System.currentTimeMillis()
        when (outcome) {
            is HubOutcome.Updated -> store.apply(outcome.config, outcome.etag, at)
            HubOutcome.Cached -> store.markAttempt(at, confirmed = true)
            HubOutcome.Unreachable -> return null
        }
        val release = store.release ?: return null
        if (!HubRules.updateWanted(BuildConfig.VERSION_CODE, release, store.bucketSeed)) {
            return CheckOutcome.UpToDate
        }
        return CheckOutcome.Available(release.asReleaseInfo())
    }

    private fun CheckOutcome.asState(): UpdateState = when (this) {
        is CheckOutcome.Available -> UpdateState.Available(release)
        CheckOutcome.UpToDate -> UpdateState.UpToDate
        CheckOutcome.NoRelease -> UpdateState.NoRelease
        CheckOutcome.RateLimited -> UpdateState.RateLimited
        is CheckOutcome.Failed -> UpdateState.Failed(reason)
    }

    fun download(release: ReleaseInfo) {
        val url = release.apkUrl ?: return
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(0)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ApkInstaller.download(getApplication(), url, release.sizeBytes, release.sha256) { percent ->
                    _state.value = UpdateState.Downloading(percent)
                }
            }
            _state.value = result.fold(
                onSuccess = { file -> UpdateState.ReadyToInstall(file, release) },
                onFailure = { error -> UpdateState.Failed(error.detailOr("download"), whileDownloading = true) }
            )
        }
    }

    fun reset() {
        _state.value = UpdateState.Idle
    }
}

/**
 * 后台的版本记录换成本地更新流程用的模型。
 *
 * 这个方向是有讲究的：remote 包不认识 ReleaseInfo，只有 update 包认识 remote——
 * 反过来的话，公告和统计那些跟「装哪个包」无关的东西会被一起拖进更新流程里。
 *
 * 只有填了网盘名和链接才生成 [Mirror]：后台允许只登记 GitHub 一条通道，那种情况下
 * 这一行不该出现，更不该出现一个点了打不开的入口。
 */
private fun HubRelease.asReleaseInfo(): ReleaseInfo {
    val mirror = if (mirrorLabel.isNotBlank() && mirrorUrl.isNotBlank()) {
        Mirror(mirrorLabel, mirrorUrl, mirrorCode)
    } else {
        null
    }
    return ReleaseInfo(
        tagName = name,
        versionName = name,
        pageUrl = pageUrl,
        apkUrl = apkUrl.takeIf { it.isNotBlank() },
        notes = notes,
        critical = critical,
        sizeBytes = sizeBytes.toLong(),
        sha256 = sha256,
        mirror = mirror
    )
}
