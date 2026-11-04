package com.qianxi.simlet.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qianxi.simlet.BuildConfig
import com.qianxi.simlet.R
import com.qianxi.simlet.data.RegionCatalog
import com.qianxi.simlet.remote.HubRules
import com.qianxi.simlet.remote.RemoteStore
import com.qianxi.simlet.ui.SimletViewModel
import com.qianxi.simlet.ui.component.CriticalUpdateRow
import com.qianxi.simlet.ui.component.HelpDialog
import com.qianxi.simlet.ui.component.IsoPicker
import com.qianxi.simlet.ui.component.NoticeSection
import com.qianxi.simlet.ui.component.NoticeDialog
import com.qianxi.simlet.ui.component.OperatorSheet
import com.qianxi.simlet.ui.component.PickerRow
import com.qianxi.simlet.ui.component.RegionSheet
import com.qianxi.simlet.ui.component.SettingGroup
import com.qianxi.simlet.ui.component.SkeletonBlock
import com.qianxi.simlet.ui.component.SlotRows
import com.qianxi.simlet.ui.englishUi
import com.qianxi.simlet.ui.theme.SimletButtonShape
import dev.reicon.HelpCircle
import dev.reicon.Refresh
import dev.reicon.Settings
import dev.reicon.Tower

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenNotices: () -> Unit,
    viewModel: SimletViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var regionSheetOpen by remember { mutableStateOf(false) }
    var operatorSheetOpen by remember { mutableStateOf(false) }
    var helpSheetOpen by remember { mutableStateOf(false) }
    var noticeSheetOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val remote = RemoteStore.of(context)
    LaunchedEffect(state.notice) {
        val feedback = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(feedback.render(context))
        viewModel.consumeNotice()
    }

    // 新公告自动弹一次：取走的同时就把这条记成「已弹过」，所以杀进程重来也不会再弹。
    // 未读点不走这里——弹过一次不等于人读过，要点开口诀那句「查看后才消失」才成立。
    val announcement = remote.notice
    LaunchedEffect(announcement?.id) {
        if (remote.takeNoticePopup(announcement) != null) noticeSheetOpen = true
    }

    AppScaffold(
        title = stringResource(R.string.app_name),
        actions = {
            IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                Icon(Refresh.Outline, contentDescription = stringResource(R.string.action_refresh))
            }
            IconButton(onClick = { helpSheetOpen = true }) {
                Icon(HelpCircle.Outline, contentDescription = stringResource(R.string.action_help))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Settings.Outline, contentDescription = stringResource(R.string.action_settings))
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val canSave = state.canSubmit && !state.plan.empty
                Button(
                    onClick = viewModel::save,
                    enabled = canSave,
                    modifier = Modifier
                        .weight(1.7f)
                        .height(56.dp),
                    shape = SimletButtonShape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    if (state.working) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_writing))
                    } else {
                        Text(stringResource(R.string.action_save))
                    }
                }
                OutlinedButton(
                    onClick = viewModel::restore,
                    enabled = state.canSubmit,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = SimletButtonShape
                ) { Text(stringResource(R.string.action_restore)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { insets ->
        AppColumn(insets) {
            // 后台标了「重要」、且这台设备在投放范围里，才多出这一行。
            // 摆在最上面不担心挡住操作：保存/还原在底部栏里，不随内容滚动。
            // 判定复用 HubRules——和设置页那次检查是同一条规则，两处不可能给出相反的答案。
            remote.release
                ?.takeIf { it.critical && HubRules.updateWanted(BuildConfig.VERSION_CODE, it, remote.bucketSeed) }
                ?.let { pending ->
                    CriticalUpdateRow(versionName = pending.name, onClick = onOpenSettings)
                }

            // 公告摆在第一组：藏在最后一组的公告等于没有公告。
            // 保存与还原在底部栏里、不随内容滚动，所以它顶不掉主操作。
            // 没配后台地址或后台没发公告时这一组不出现。
            remote.notice?.let { notice ->
                NoticeSection(
                    notice = notice,
                    unread = remote.noticeUnread(notice),
                    // 点这一行进的是公告列表，不再是弹窗：弹窗只留给「刚推送那一次」。
                    // 未读点仍然在这里清——进列表就等于读过了，和原来点弹窗清掉是同一个时机。
                    onOpen = {
                        onOpenNotices()
                        remote.markNoticeSeen(notice.id)
                    }
                )
            }

            SettingGroup(title = stringResource(R.string.section_sim_cards)) {
                when {
                    state.loading -> Column(Modifier.padding(20.dp)) { SkeletonBlock(lines = 2) }
                    state.slots.isEmpty() -> Column(Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.sim_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // 读不到卡的原因只有 shell 那一侧知道，留成原文，
                        // 用户截图就能定位，不用我们隔着反馈猜。
                        state.slotsDiagnosis?.let { reason ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> SlotRows(
                        slots = state.slots,
                        selectedIndex = state.selectedIndex,
                        onSelect = viewModel::selectSlot
                    )
                }
            }

            state.failure?.let { problem ->
                SettingGroup(title = stringResource(problem.headlineRes)) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                        problem.detail?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = viewModel::refresh) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }

            val iso = state.targetIso
            val english = englishUi()
            SettingGroup(title = stringResource(R.string.section_country)) {
                IsoPicker(
                    selected = iso,
                    onPick = viewModel::pickIso,
                    onOpenAll = { regionSheetOpen = true },
                    modifier = Modifier.padding(16.dp)
                )
            }

            SettingGroup(title = stringResource(R.string.section_operator)) {
                PickerRow(
                    label = stringResource(R.string.override_to),
                    value = state.carrier.takeIf { it.isNotBlank() },
                    placeholder = stringResource(R.string.keep_current),
                    icon = Tower.Outline,
                    onClick = { operatorSheetOpen = true }
                )
            }

        }
    }

    RegionSheet(
        visible = regionSheetOpen,
        onDismiss = { regionSheetOpen = false },
        onPick = viewModel::pickIso
    )

    OperatorSheet(
        visible = operatorSheetOpen,
        currentIso = state.targetIso,
        selected = state.carrier,
        onDismiss = { operatorSheetOpen = false },
        onPick = viewModel::pickCarrier
    )

    HelpDialog(visible = helpSheetOpen, onDismiss = { helpSheetOpen = false })

    NoticeDialog(
        visible = noticeSheetOpen,
        notice = remote.notice,
        onDismiss = { noticeSheetOpen = false }
    )
}
