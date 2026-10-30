package com.qianxi.simlet.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qianxi.simlet.R
import com.qianxi.simlet.update.ReleaseInfo
import com.qianxi.simlet.update.UpdateState
import com.qianxi.simlet.ui.theme.SimletButtonShape
import dev.reicon.Copy
import dev.reicon.Qr
import dev.reicon.Refresh
import dev.reicon.Tag

/**
 * 检查更新分组。
 *
 * 参考稿的每一屏都是「组标题 + 一卡行」，所以这里不摆按钮，而是把每个可用动作摊成一行：
 * 有待装包就多出「立即安装」，有下载地址就多出「下载升级包」，动作即状态。
 */
@Composable
fun UpdateSection(
    state: UpdateState,
    currentVersion: String,
    onCheck: () -> Unit,
    onDownload: (ReleaseInfo) -> Unit,
    onInstall: () -> Unit,
    onOpenPage: () -> Unit
) {
    val (status, detail) = describe(state)
    val release = (state as? UpdateState.Available)?.release
        ?: (state as? UpdateState.ReadyToInstall)?.release

    SettingGroup(title = stringResource(R.string.settings_update_group)) {
        SettingRow(
            title = stringResource(R.string.update_current, currentVersion),
            icon = Tag.Outline,
            value = status,
            valueColor = if (state is UpdateState.Available || state is UpdateState.ReadyToInstall)
                MaterialTheme.colorScheme.onSurface else null
        )
        if (detail != null) {
            RowDivider()
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = TextStart, end = 24.dp, top = 12.dp, bottom = 12.dp)
            )
        }
        if (state is UpdateState.Downloading) {
            RowDivider()
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
        if (state is UpdateState.ReadyToInstall) {
            RowDivider()
            SettingRow(
                title = stringResource(R.string.update_install),
                icon = Refresh.Outline,
                onClick = onInstall
            )
        } else if (release?.apkUrl != null && state is UpdateState.Available) {
            RowDivider()
            SettingRow(
                title = stringResource(R.string.update_download),
                icon = Refresh.Outline,
                onClick = { onDownload(release) }
            )
        }
        if (release?.pageUrl?.isNotBlank() == true) {
            RowDivider()
            SettingRow(
                title = stringResource(R.string.update_open_page),
                tail = RowTail.Next,
                onClick = onOpenPage
            )
        }
        RowDivider()
        SettingRow(
            title = stringResource(R.string.update_check),
            icon = Refresh.Outline,
            value = if (state is UpdateState.Checking) stringResource(R.string.update_checking) else null,
            tail = RowTail.Next,
            enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
            onClick = onCheck
        )
    }
}

/** 公众号渠道：一行入口，二维码与复制名称收进弹层。 */
@Composable
fun WechatRow(onClick: () -> Unit) {
    SettingGroup(title = stringResource(R.string.update_channel_group)) {
        SettingRow(
            title = stringResource(R.string.update_channel),
            icon = Qr.Outline,
            badge = stringResource(R.string.update_channel_name),
            value = null,
            tail = RowTail.Next,
            onClick = onClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WechatSheet(visible: Boolean, onCopyName: () -> Unit, onDismiss: () -> Unit) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            // 没有 fillMaxWidth 时这一列只包到最宽子元素的宽度，
            // horizontalAlignment 是在这块窄宽度里居中，整块仍旧贴着左边。
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.update_channel), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.update_channel_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            // 不裁圆角、不加边框：四角是定位点，clip 会吃掉模块；二维码自带留白。
            Image(
                painter = painterResource(R.drawable.wechat_qrcode),
                contentDescription = stringResource(R.string.update_channel_qr),
                modifier = Modifier.size(180.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.update_channel_name),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(24.dp))
            // 原来是一整行只有文字的 SettingRow，看着像说明不像按钮；
            // 改成居中的实心按钮，前面带标准复制图标。
            Button(
                onClick = onCopyName,
                shape = SimletButtonShape,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(
                    Copy.Outline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.update_channel_copy))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 状态即文案：每种结果一行状态 + 可选详情，不拼字符串。还没点过就不占行尾。 */
@Composable
private fun describe(state: UpdateState): Pair<String?, String?> = when (state) {
    UpdateState.Idle -> null to null
    UpdateState.Checking -> stringResource(R.string.update_checking) to null
    UpdateState.UpToDate -> stringResource(R.string.update_up_to_date) to null
    is UpdateState.Available -> stringResource(R.string.update_available, state.release.versionName) to
        (state.release.notes.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.update_no_asset).takeIf { state.release.apkUrl == null })

    UpdateState.NoRelease -> stringResource(R.string.update_no_release) to
        stringResource(R.string.update_no_release_detail)

    UpdateState.RateLimited -> stringResource(R.string.update_rate_limited) to
        stringResource(R.string.update_rate_limited_detail)

    is UpdateState.Failed -> stringResource(
        if (state.whileDownloading) R.string.update_download_failed else R.string.update_failed
    ) to state.reason

    is UpdateState.Downloading -> stringResource(R.string.update_downloading, state.percent) to null
    is UpdateState.ReadyToInstall -> stringResource(R.string.update_ready, state.release.versionName) to null
}
