package com.qianxi.simlet.ui.screen

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.qianxi.simlet.R
import com.qianxi.simlet.core.ShizukuLink
import com.qianxi.simlet.ui.component.LabeledValue
import com.qianxi.simlet.ui.component.RowDivider
import com.qianxi.simlet.ui.component.SettingGroup
import com.qianxi.simlet.ui.component.SettingRow
import com.qianxi.simlet.ui.component.StatusDot
import com.qianxi.simlet.ui.openWebPage
import com.qianxi.simlet.ui.theme.SimletButtonShape
import dev.reicon.Refresh
import dev.reicon.Settings
import kotlinx.coroutines.delay

enum class SetupStage { NOT_INSTALLED, SERVICE_DOWN, PERMISSION_PENDING, SERVER_TOO_OLD }

private const val SHIZUKU_DOWNLOAD = "https://github.com/rushiranpise/Shizuku-Next"

fun resolveSetupStage(context: android.content.Context): SetupStage = when {
    !ShizukuLink.installed(context) -> SetupStage.NOT_INSTALLED
    !ShizukuLink.binderAlive() -> SetupStage.SERVICE_DOWN
    !ShizukuLink.granted() -> SetupStage.PERMISSION_PENDING
    !ShizukuLink.delegateCapable() -> SetupStage.SERVER_TOO_OLD
    else -> SetupStage.PERMISSION_PENDING
}

@Composable
fun SetupScreen(
    stage: SetupStage,
    onRequestPermission: () -> Unit,
    onRecheck: () -> Unit
) {
    val context = LocalContext.current

    // 服务端的补丁版本号是 attach 回包填进来的，比 binder 到达晚几十毫秒：
    // 刚进这一页就读，读到的是「还不知道」，那一行会显示成不支持。给它一次重读，
    // 免得人对着一个灰点去升级 Shizuku——而这台机器其实什么都好。
    var delegateOk by remember { mutableStateOf(ShizukuLink.delegateCapable()) }
    LaunchedEffect(Unit) {
        if (!delegateOk) {
            delay(400)
            delegateOk = ShizukuLink.delegateCapable()
        }
    }

    // 停在这一页时每秒自己再看一眼：服务起来、授权生效这些时刻都不该要人退出重进才知道。
    // 这一页只在没就绪时存在，进了首页它就被移出组合，循环跟着协程一起停。
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            onRecheck()
            delegateOk = ShizukuLink.delegateCapable()
        }
    }

    val (titleRes, detailRes, action) = when (stage) {
        SetupStage.NOT_INSTALLED -> Triple(
            R.string.setup_need_install_title,
            R.string.setup_need_install_detail,
            { context.openWebPage(SHIZUKU_DOWNLOAD) }
        )
        SetupStage.SERVICE_DOWN -> Triple(
            R.string.setup_service_down_title,
            R.string.setup_service_down_detail,
            { if (!ShizukuLink.launchHost(context)) context.openWebPage(SHIZUKU_DOWNLOAD) }
        )
        SetupStage.PERMISSION_PENDING -> Triple(
            R.string.setup_permission_title,
            R.string.setup_permission_detail,
            onRequestPermission
        )
        SetupStage.SERVER_TOO_OLD -> Triple(
            R.string.setup_old_server_title,
            R.string.setup_old_server_detail,
            { context.openWebPage(SHIZUKU_DOWNLOAD) }
        )
    }

    AppScaffold(title = stringResource(R.string.setup_title)) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(36.dp))
            Icon(
                Settings.Outline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(detailRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            // 公告不挂在这一页。这一页只做一件事：把权限拿到手；
            // 授权还没成，先把一条和授权无关的公告弹到人脸上，等于在最要紧的那一步中间插了个岔。
            SettingGroup(title = stringResource(R.string.setup_env_check)) {
                LabeledValue(
                    stringResource(R.string.setup_android_version),
                    stringResource(
                        if (Build.VERSION.SDK_INT >= 36) R.string.setup_android_value_newer else R.string.setup_android_value,
                        systemNickname(),
                        Build.VERSION.SDK_INT
                    )
                )
                RowDivider()
                LabeledValue(
                    stringResource(R.string.setup_shizuku_service),
                    stringResource(if (ShizukuLink.binderAlive()) R.string.setup_running else R.string.setup_not_running)
                )
                RowDivider()
                LabeledValue(
                    stringResource(R.string.setup_shizuku_version),
                    if (ShizukuLink.binderAlive()) ShizukuLink.serverVersion().toString()
                    else stringResource(R.string.setup_unknown),
                    emphasized = delegateOk
                )
                RowDivider()
                SettingRow(
                    title = stringResource(R.string.setup_granted),
                    trailing = {
                        StatusDot(ready = ShizukuLink.granted(), modifier = Modifier.padding(start = 10.dp))
                    }
                )
                RowDivider()
                SettingRow(
                    title = stringResource(R.string.setup_delegate_ok),
                    trailing = {
                        StatusDot(ready = delegateOk, modifier = Modifier.padding(start = 10.dp))
                    }
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = action,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = SimletButtonShape,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) { Text(stringResource(actionLabel(stage))) }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRecheck,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = SimletButtonShape
            ) {
                Icon(Refresh.Outline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.setup_recheck))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@StringRes
private fun actionLabel(stage: SetupStage) = when (stage) {
    SetupStage.NOT_INSTALLED -> R.string.setup_action_get
    SetupStage.SERVICE_DOWN -> R.string.setup_action_open
    SetupStage.PERMISSION_PENDING -> R.string.setup_action_request
    SetupStage.SERVER_TOO_OLD -> R.string.setup_action_upgrade
}

/**
 * Android 12 以后版本号与 marketed name 一致，早期版本按代号回推。
 *
 * 这里只回版本号本身，「或更新」那半句交给字符串资源，英文下才不会变成 "16 或更新"。
 */
private fun systemNickname(): String = when (Build.VERSION.SDK_INT) {
    in 36..Int.MAX_VALUE -> "16"
    35 -> "15"
    34 -> "14"
    33 -> "13"
    32 -> "12L"
    31 -> "12"
    30 -> "11"
    29 -> "10"
    else -> "8/9"
}
