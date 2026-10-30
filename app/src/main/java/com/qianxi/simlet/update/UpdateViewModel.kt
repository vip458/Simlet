package com.qianxi.simlet.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qianxi.simlet.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        val current = BuildConfig.VERSION_NAME
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            _state.value = when (val outcome = withContext(Dispatchers.IO) { GitHubClient.fetchLatest(current) }) {
                is CheckOutcome.Available -> UpdateState.Available(outcome.release)
                CheckOutcome.UpToDate -> UpdateState.UpToDate
                CheckOutcome.NoRelease -> UpdateState.NoRelease
                CheckOutcome.RateLimited -> UpdateState.RateLimited
                is CheckOutcome.Failed -> UpdateState.Failed(outcome.reason)
            }
        }
    }

    fun download(release: ReleaseInfo) {
        val url = release.apkUrl ?: return
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(0)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                ApkInstaller.download(getApplication(), url) { percent ->
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
