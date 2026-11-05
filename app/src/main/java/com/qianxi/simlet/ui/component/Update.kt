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
import com.qianxi.simlet.update.Mirror
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
    onOpenPage: () -> Unit,
    onOpenMirror: (Mirror) -> Unit
) {
    val (status, detail) = describe(state)
    val release = (state as? UpdateState.Available)?.release
        ?: (state as? UpdateState.ReadyToInstall)?.release
    // 镜像行只在「发现新版本」时出现：ReadyToInstall 说明包已经在本地了，再给一条下载入口只会让人
    // 以为下载失败。它也不参与应用内下载——服务器上不存安装包，这一行点了交给浏览器。
    val mirror = (state as? UpdateState.Available)?.release?.mirror

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
        if (mirror != null) {
            RowDivider()
            SettingRow(
                title = stringResource(R.string.update_mirror, mirror.label),
                // 提取码直接摆在尾值上：不复制也能看见，网盘页面要输的就是这一段。
                value = mirror.code.takeIf { it.isNotBlank() },
                tail = RowTail.Next,
                onClick = { onOpenMirror(mirror) }
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

/**
 * 后台标了「重要」且这台设备在投放范围里时，首页顶部多出这一行。
 *
 * 就这一行：不弹窗、不拦操作、也不在设置页再说一遍。fc 的语义是「值得提醒」，不是「必须升级」——
 * 工具类应用不该因为版本旧就把用户锁在门外。点击不去下载，而是送到设置页那一组，
 * 因为「哪个渠道下载、要不要现在装」是用户在那一页本来就会看的地方。
 */
@Composable
fun CriticalUpdateRow(versionName: String, onClick: () -> Unit) {
    SettingGroup(title = stringResource(R.string.settings_update_group)) {
        SettingRow(
            title = stringResource(R.string.update_critical, versionName),
            icon = Tag.Outline,
            tail = RowTail.Next,
            onClick = onClick
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

/**
 * 发现新版本的居中弹窗。
 *
 * 只在「刚查出有新版本」那一次出现，负责第一次告知；设置页那一组动作行原样留着——
 * 「从哪个渠道下、要不要现在装」是会被反复回来看的东西，收进弹窗就找不着了。
 *
 * 镜像那颗按钮**按后台有没有配出现**，不是两个弹窗：没配网盘就少一颗，窗体本身一模一样。
 * 没有安装包时主按钮换成「查看发布页」，因为「去下载」点了什么也不会发生。
 */
@Composable
fun UpdateDialog(
    visible: Boolean,
    release: ReleaseInfo?,
    currentVersion: String,
    onDismiss: () -> Unit,
    onDownload: (ReleaseInfo) -> Unit,
    onOpenPage: () -> Unit,
    onOpenMirror: (Mirror) -> Unit
) {
    val target = release
    val mirror = target?.mirror
    SimletDialog(
        // 关窗时 release 往往还在状态里，所以 visible 由调用方说了算；
        // 这里再叠一个 target != null 只是不让它弹出一个空版本号的窗。
        visible = visible && target != null,
        title = stringResource(R.string.update_available, target?.versionName.orEmpty()),
        onDismiss = onDismiss,
        confirmText = if (target?.apkUrl != null) stringResource(R.string.update_go_download)
        else stringResource(R.string.update_open_page),
        onConfirm = {
            val found = target ?: return@SimletDialog
            if (found.apkUrl != null) onDownload(found) else onOpenPage()
        },
        secondaryText = mirror?.let { stringResource(R.string.update_mirror, it.label) },
        onSecondary = mirror?.let { { onOpenMirror(it) } },
        dismissText = stringResource(R.string.dialog_later)
    ) {
        Text(
            target?.notes?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.update_dialog_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.update_dialog_meta, currentVersion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 状态即文案：每种结果一行状态 + 可选详情，不拼字符串。还没点过就不占行尾。 */
@Composable
private fun describe(state: UpdateState): Pair<String?, String?> = when (state) {
    UpdateState.Idle -> null to null
    UpdateState.Checking -> stringResource(R.string.update_checking) to null
    UpdateState.UpToDate -> stringResource(R.string.update_up_to_date) to null
    is UpdateState.Available -> {
        // 后台带的更新说明是短文本（服务端裁到 400 字，写的是「修复了什么」），铺在这一行下面刚好。
        // 走 GitHub 那条通道时说明是空的，也不去抓发布页正文：markdown 长度不受我们控制，
        // 整段铺进来会把下面的「下载升级包」挤出屏幕（实测 v1.0.0 的正文就把它顶没了）。
        // 没有安装包时宁可只说这一句——用户此刻要的是「去哪儿手动下」，不是发布说明。
        val note: String? = if (state.release.notes.isNotBlank()) {
            state.release.notes
        } else {
            stringResource(R.string.update_no_asset).takeIf { state.release.apkUrl == null }
        }
        stringResource(R.string.update_available, state.release.versionName) to note
    }

    UpdateState.NoRelease -> stringResource(R.string.update_no_release) to
        stringResource(R.string.update_no_release_detail)

    UpdateState.RateLimited -> stringResource(R.string.update_rate_limited) to
        stringResource(R.string.update_rate_limited_detail)

    is UpdateState.Failed -> stringResource(
        when {
            // 校验不通过时包是下全了的，说「下载未完成」会把人往网络那头带，
            // 而那正是最该看清的时刻：要么后台登记错了，要么这个包被动过。
            state.reason.startsWith("digest") -> R.string.update_digest_failed
            state.whileDownloading -> R.string.update_download_failed
            else -> R.string.update_failed
        }
    ) to state.reason

    is UpdateState.Downloading -> stringResource(R.string.update_downloading, state.percent) to null
    is UpdateState.ReadyToInstall -> stringResource(R.string.update_ready, state.release.versionName) to null
}
