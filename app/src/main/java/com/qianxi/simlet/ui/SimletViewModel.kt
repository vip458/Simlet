package com.qianxi.simlet.ui

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qianxi.simlet.R
import com.qianxi.simlet.core.OverrideWriter
import com.qianxi.simlet.core.PlanStore
import com.qianxi.simlet.core.SlotReader
import com.qianxi.simlet.model.OverridePlan
import com.qianxi.simlet.model.SimSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeState(
    val slots: List<SimSlot> = emptyList(),
    val selectedIndex: Int = 0,
    val loading: Boolean = true,
    val failure: Feedback? = null,
    val targetIso: String? = null,
    val carrier: String = "",
    val working: Boolean = false,
    val notice: Feedback? = null
) {
    val current: SimSlot? get() = slots.getOrNull(selectedIndex)
    val canSubmit: Boolean get() = current != null && !working && !loading
    val plan: OverridePlan get() = OverridePlan.from(targetIso, carrier.takeIf { it.isNotBlank() })
}

/**
 * 一次操作的结果。
 *
 * 标题走字符串资源，因此能随语言切换；详情保留 core 抛出的原文——那些是中文诊断，
 * 翻译要动 12 处抛出点，代价远大于收益，所以只在英文界面下作为次要一行展示。
 */
data class Feedback(
    @StringRes val headlineRes: Int,
    val formatArgs: List<Any> = emptyList(),
    val detail: String? = null
) {
    /** Snackbar 只能给一段文本，标题与详情用换行叠在一起。 */
    fun render(context: Context): String =
        context.getString(headlineRes, *formatArgs.toTypedArray()) + (detail?.let { "\n$it" } ?: "")
}

class SimletViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, failure = null) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    SlotReader.slots(app).also { PlanStore.adopt(app, it) }
                }
            }
            _state.update { current ->
                val slots = result.getOrDefault(emptyList())
                current.copy(
                    slots = slots,
                    loading = false,
                    failure = result.exceptionOrNull()?.let(::describe),
                    selectedIndex = current.selectedIndex.coerceAtMost((slots.size - 1).coerceAtLeast(0))
                )
            }
        }
    }

    fun selectSlot(index: Int) = _state.update { it.copy(selectedIndex = index) }

    fun pickIso(iso: String?) = _state.update { it.copy(targetIso = iso) }

    fun pickCarrier(name: String) = _state.update { it.copy(carrier = name) }

    fun save() = submit { slot, plan, app ->
        OverrideWriter.apply(app, slot, plan).map {
            PlanStore.remember(app, slot.slotIndex, plan)
            Feedback(R.string.done_saved)
        }
    }

    fun restore() {
        val slot = _state.value.current ?: return
        val app = getApplication<Application>()
        _state.update { it.copy(working = true, notice = null) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { OverrideWriter.restore(app, slot) }
            outcome.onSuccess { withContext(Dispatchers.IO) { PlanStore.remember(app, slot.slotIndex, OverridePlan()) } }
            _state.update {
                it.copy(
                    working = false,
                    notice = outcome.fold({ Feedback(R.string.done_restored, listOf(slot.slotNumber)) }, ::describe)
                )
            }
            refreshSilently()
        }
    }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    private fun submit(action: (SimSlot, OverridePlan, Application) -> Result<Feedback>) {
        val snapshot = _state.value
        val slot = snapshot.current ?: return
        if (snapshot.plan.empty) {
            _state.update { it.copy(notice = Feedback(R.string.need_choice)) }
            return
        }
        val app = getApplication<Application>()
        _state.update { it.copy(working = true, notice = null) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { action(slot, snapshot.plan, app) }
            _state.update {
                it.copy(working = false, notice = outcome.fold({ it }, ::describe))
            }
            if (outcome.isSuccess) refreshSilently()
        }
    }

    /** 写完后重新读一次系统真实状态，让「当前生效」区块立刻反映结果。 */
    private fun refreshSilently() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val slots = runCatching { withContext(Dispatchers.IO) { SlotReader.slots(app) } }.getOrNull()
                ?: return@launch
            _state.update { it.copy(slots = slots, loading = false) }
        }
    }

    private fun describe(error: Throwable): Feedback {
        val root = generateSequence(error) { it.cause }.last()
        val detail = root.message?.takeIf { it.isNotBlank() }
            ?: getApplication<Application>().getString(R.string.err_failed_hint)
        return if (root is SecurityException) {
            Feedback(R.string.err_permission, detail = detail)
        } else {
            Feedback(R.string.err_failed, detail = detail)
        }
    }
}
