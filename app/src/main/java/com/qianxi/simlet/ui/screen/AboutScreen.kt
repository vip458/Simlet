package com.qianxi.simlet.ui.screen

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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

/** QQ 群分享链，带群号与加群凭据；换群要重新生成。 */
private const val QQ_GROUP_URL =
    "https://qun.qq.com/universal-share/share?ac=1&authKey=vEK5KHw%2FmB09yJfTUGb8zNt0zh20n6i859rEQQRXyVKMXT%2BHYQ8o%2BQy6hr243gN6" +
        "&busi_data=eyJncm91cENvZGUiOiIxMDQ5MjExNDg0IiwidG9rZW4iOiJtWUc3aVlwM01hUEN1TDN2UkhHOVlEUWk2TUQ1QmgwZk41SnlvSHVqOHJMSkNlNHFkNGZNY0dZbjk0Vk05QW1jIiwidWluIjoiNDU4NjU1NzMyIn0%3D" +
        "&data=xNOHX1aej9QNrV7KWcTHAMAis85bkBDqpi_9qWz-OclTQPpv5LdkAfE_wqAHNmjlN095vav5KQ11BEmav6CZOQ&svctype=4&tempid=h5_group_info"

/**
 * 关于页：居中 logo 块 + 行列表 + 底部灰脚注，全部照参考稿那张关于页的结构。
 *
 * 使用提示在首页顶栏的帮助里，权限与数据在设置页——两者都是「用的时候才需要看」。
 * 第三方许可不再在这页铺开：它有自己的列表页，图标集也在里面。
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    val context = LocalContext.current
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
                    tail = RowTail.Next,
                    onClick = { context.openWebPage(QQ_GROUP_URL) }
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
