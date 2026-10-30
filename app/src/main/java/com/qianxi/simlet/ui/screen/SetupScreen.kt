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

enum class SetupStage { NOT_INSTALLED, SERVICE_DOWN, PERMISSION_PENDING, SERVER_TOO_OLD }

private const val SHIZUKU_DOWNLOAD = "https://github.com/RikkaApps/Shizuku-Releases"

fun resolveSetupStage(context: android.content.Context): SetupStage = when {
    !ShizukuLink.installed(context) -> SetupStage.NOT_INSTALLED
    !ShizukuLink.binderAlive() -> SetupStage.SERVICE_DOWN
    !ShizukuLink.granted() -> SetupStage.PERMISSION_PENDING
    !ShizukuLink.delegateCapable() -> SetupStage.SERVER_TOO_OLD
    else -> SetupStage.PERMISSION_PENDING
}

@Composable
fun SetupScreen(stage: SetupStage, onRequestPermission: () -> Unit, onRecheck: () -> Unit) {
    val context = LocalContext.current

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
                    emphasized = ShizukuLink.binderAlive() && ShizukuLink.delegateCapable()
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
                        StatusDot(ready = ShizukuLink.delegateCapable(), modifier = Modifier.padding(start = 10.dp))
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
