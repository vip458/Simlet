package com.qianxi.simlet.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qianxi.simlet.BuildConfig
import com.qianxi.simlet.R
import com.qianxi.simlet.licenses.openSourceEntries
import com.qianxi.simlet.ui.component.RowDivider
import com.qianxi.simlet.ui.component.SettingGroup
import com.qianxi.simlet.ui.component.SettingRow
import com.qianxi.simlet.ui.component.RowTail
import com.qianxi.simlet.ui.openWebPage
import dev.reicon.Code
import dev.reicon.Globe
import dev.reicon.MessageCircle

private const val PROJECT_URL = "https://github.com/vip458/Simlet"

/**
 * 反馈群只放群号：QQ 的加群跳转链接带一次性签名 token，
 * 那种 URL 公开在仓库里等于把群主的凭据发出去，而且会过期。
 * 所以这里点一下复制群号，用户自己去 QQ 搜。
 */
private const val QQ_GROUP = "551772286"

/**
 * 关于页：居中 logo 块 + 行列表 + 底部灰脚注，全部照参考稿那张关于页的结构。
 *
 * 使用提示在首页顶栏的帮助里，权限与数据在设置页——两者都是「用的时候才需要看」。
 * 第三方许可不再在这页铺开：它有自己的列表页，图标集也在里面。
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copiedHint = stringResource(R.string.about_feedback_copied, QQ_GROUP)
    AppScaffold(title = stringResource(R.string.about_title), onBack = onBack) { insets ->
        AppColumn(insets) {
            SettingGroup {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.about_version_line, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.about_developer_line),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SettingGroup(title = stringResource(R.string.about_service_group)) {
                SettingRow(
                    title = stringResource(R.string.about_project),
                    icon = Globe.Outline,
                    tail = RowTail.Next,
                    onClick = { context.openWebPage(PROJECT_URL) }
                )
                RowDivider()
                SettingRow(
                    title = stringResource(R.string.about_feedback),
                    icon = MessageCircle.Outline,
                    value = stringResource(R.string.about_feedback_value),
                    onClick = {
                        clipboard.setText(AnnotatedString(QQ_GROUP))
                        Toast.makeText(context, copiedHint, Toast.LENGTH_SHORT).show()
                    }
                )
                RowDivider()
                SettingRow(
                    title = stringResource(R.string.about_license),
                    icon = Code.Outline,
                    value = pluralStringResource(
                        R.plurals.count_items,
                        openSourceEntries.size,
                        openSourceEntries.size
                    ),
                    tail = RowTail.Next,
                    onClick = onOpenLicenses
                )
            }

            Text(
                stringResource(R.string.about_footer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )
        }
    }
}
