package com.qianxi.simlet.ui.screen

import android.os.Build
import android.widget.Toast
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
import dev.reicon.MessageCircle
import dev.reicon.Moon
import dev.reicon.Shield

private const val QQ_GROUP = "551772286"

/**
 * 点「反馈交流」跳去 QQ 加群。
 *
 * 这是 QQ「分享群」给出来的那条 universal-share 链接：busi_data 是 base64，
 * 解出来含群主 uin 和一个 token。更干净的做法是群二维码那条
 * `qm.qq.com/cgi-bin/qm/qr?k=…`（只带群自己的 key），但 QQ 当前版本分享出来
 * 只有 universal-share 这一种，所以就用它。
 *
 * 万一它过期或被人拿去刷群：把这里换成空串，这一行会退回「复制群号」，
 * 不需要动别的代码。
 */
private const val QQ_JOIN_URL =
    "https://qun.qq.com/universal-share/share?ac=1" +
        "&authKey=izvrQCNarsbPQQG1Y8IsSeSp8%2FIz%2FIdQ0awH3sTSi%2BBSny%2F7axHzl7MEe4RoG9xX" +
        "&busi_data=eyJncm91cENvZGUiOiI1NTE3NzIyODYiLCJ0b2tlbiI6IklYQ0FFSWs0YkV0TWxqSmpWbXJ4K0c1WE1CZ3ZpODJydUtPYnEwRjJ0U3BQMmxLbFhCSEx1SDdpaEtVMnpPSEUiLCJ1aW4iOiI0NTg2NTU3MzIifQ%3D%3D" +
        "&data=Xmm0zFg5KfMXUs_Vz37BOSjXxNShffk2e7erhOtHAQPQy0NaCJ8P1wTDQaYjPf-0yQRMgq-sWOWpkVHdEtpTPQ" +
        "&svctype=4&tempid=h5_group_info"

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
    val qqCopied = stringResource(R.string.about_feedback_copied, QQ_GROUP)
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
                    title = stringResource(R.string.about_feedback),
                    icon = MessageCircle.Outline,
                    value = stringResource(R.string.about_feedback_value),
                    tail = if (QQ_JOIN_URL.isEmpty()) RowTail.None else RowTail.Next,
                    onClick = {
                        if (QQ_JOIN_URL.isEmpty()) {
                            clipboard.setText(AnnotatedString(QQ_GROUP))
                            Toast.makeText(context, qqCopied, Toast.LENGTH_SHORT).show()
                        } else {
                            context.openWebPage(QQ_JOIN_URL)
                        }
                    }
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
