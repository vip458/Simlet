package com.qianxi.simlet.ui.screen

import android.os.Build
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.qianxi.simlet.BuildConfig
import com.qianxi.simlet.R
import com.qianxi.simlet.ui.UiPrefs
import com.qianxi.simlet.ui.component.InfoSheet
import com.qianxi.simlet.ui.component.OptionSheet
import com.qianxi.simlet.ui.component.RowDivider
import com.qianxi.simlet.ui.component.SettingGroup
import com.qianxi.simlet.ui.component.SettingRow
import com.qianxi.simlet.ui.component.RowTail
import com.qianxi.simlet.ui.component.UpdateSection
import com.qianxi.simlet.ui.component.WechatRow
import com.qianxi.simlet.ui.component.WechatSheet
import com.qianxi.simlet.ui.openWebPage
import com.qianxi.simlet.ui.theme.NightMode
import com.qianxi.simlet.update.ApkInstaller
import com.qianxi.simlet.update.UpdateState
import com.qianxi.simlet.update.UpdateViewModel
import dev.reicon.Globe
import dev.reicon.InfoCircle
import dev.reicon.Language
import dev.reicon.Moon
import dev.reicon.Shield

/** 设置页：外观、语言、更新、渠道，以及通往关于页与权限说明的入口。 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onLanguageChange: (String?) -> Unit
) {
    val context = LocalContext.current
    val prefs = UiPrefs.of(context)
    val updateVm: UpdateViewModel = viewModel()
    val updateState by updateVm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val channelName = stringResource(R.string.update_channel_name)
    val needInstallPermission = stringResource(R.string.update_install_blocked_detail)
    val copied = stringResource(R.string.update_copied)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var nightSheetOpen by remember { mutableStateOf(false) }
    var languageSheetOpen by remember { mutableStateOf(false) }
    var wechatSheetOpen by remember { mutableStateOf(false) }
    var privacySheetOpen by remember { mutableStateOf(false) }

    val nightLabels = listOf(
        stringResource(R.string.night_follow),
        stringResource(R.string.night_light),
        stringResource(R.string.night_dark)
    )
    val languageLabels = listOf(
        stringResource(R.string.lang_follow),
        stringResource(R.string.lang_zh),
        stringResource(R.string.lang_en)
    )
    val nightIndex = prefs.nightMode.ordinal
    val languageIndex = when (prefs.localeTag) {
        "zh" -> 1
        "en" -> 2
        else -> 0
    }

    // 下载完成后系统还没给安装权限：先提示，再把用户送到授权页，包留在原地等他回来。
    LaunchedEffect(updateState) {
        if (updateState !is UpdateState.ReadyToInstall) return@LaunchedEffect
        if (ApkInstaller.canInstall(context)) return@LaunchedEffect
        scope.launch { snackbarHostState.showSnackbar(needInstallPermission) }
        context.startActivity(ApkInstaller.permissionIntent(context))
    }

    AppScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { insets ->
        AppColumn(insets) {
            SettingGroup(title = stringResource(R.string.settings_appearance)) {
                SettingRow(
                    title = stringResource(R.string.settings_night_mode),
                    icon = Moon.Outline,
                    value = nightLabels[nightIndex],
                    tail = RowTail.Picker,
                    onClick = { nightSheetOpen = true }
                )
            }

            SettingGroup(title = stringResource(R.string.settings_language)) {
                SettingRow(
                    title = stringResource(R.string.settings_language),
                    icon = Language.Outline,
                    value = languageLabels[languageIndex],
                    tail = RowTail.Picker,
                    onClick = { languageSheetOpen = true }
                )
            }

            UpdateSection(
                state = updateState,
                currentVersion = BuildConfig.VERSION_NAME,
                onCheck = updateVm::check,
                onDownload = updateVm::download,
                onInstall = {
                    val file = (updateState as? UpdateState.ReadyToInstall)?.file ?: return@UpdateSection
                    if (ApkInstaller.canInstall(context)) {
                        context.startActivity(ApkInstaller.installIntent(context, file))
                    } else {
                        context.startActivity(ApkInstaller.permissionIntent(context))
                    }
                },
                onOpenPage = {
                    val url = (updateState as? UpdateState.Available)?.release?.pageUrl
                    if (!url.isNullOrBlank()) context.openWebPage(url)
                }
            )

            WechatRow(onClick = { wechatSheetOpen = true })

            SettingGroup(title = stringResource(R.string.settings_about_group)) {
                SettingRow(
                    title = stringResource(R.string.settings_open_about),
                    icon = InfoCircle.Outline,
                    tail = RowTail.Next,
                    onClick = onOpenAbout
                )
                RowDivider()
                SettingRow(
                    title = stringResource(R.string.about_privacy),
                    icon = Shield.Outline,
                    tail = RowTail.Next,
                    onClick = { privacySheetOpen = true }
                )
            }
        }
    }

    OptionSheet(
        visible = nightSheetOpen,
        title = stringResource(R.string.settings_night_mode),
        options = nightLabels,
        selectedIndex = nightIndex,
        onDismiss = { nightSheetOpen = false },
        onSelect = { prefs.setNightMode(NightMode.entries[it]) }
    )

    OptionSheet(
        visible = languageSheetOpen,
        title = stringResource(R.string.settings_language),
        options = languageLabels,
        selectedIndex = languageIndex,
        onDismiss = { languageSheetOpen = false },
        onSelect = { onLanguageChange(listOf<String?>(null, "zh", "en")[it]) }
    )

    WechatSheet(
        visible = wechatSheetOpen,
        onDismiss = { wechatSheetOpen = false },
        onCopyName = {
            clipboard.setText(AnnotatedString(channelName))
            // 先收起弹层再报「已复制」：snackbar 挂在页面底层，
            // 弹层不关的话点了按钮什么反馈都看不见。
            wechatSheetOpen = false
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch { snackbarHostState.showSnackbar(copied) }
        }
    )

    InfoSheet(
        visible = privacySheetOpen,
        title = stringResource(R.string.about_privacy),
        body = stringResource(R.string.about_privacy_body),
        onDismiss = { privacySheetOpen = false }
    )
}
