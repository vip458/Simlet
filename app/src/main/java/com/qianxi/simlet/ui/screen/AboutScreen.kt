package com.qianxi.simlet.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.qianxi.simlet.remote.HubRules
import com.qianxi.simlet.ui.component.RowDivider
import com.qianxi.simlet.ui.component.SettingGroup
import com.qianxi.simlet.ui.component.SettingRow
import com.qianxi.simlet.ui.component.RowTail
import com.qianxi.simlet.ui.openWebPage
import dev.reicon.Code
import dev.reicon.Globe

private const val PROJECT_URL = "https://github.com/vip458/Simlet"

/**
 * 关于页：居中 logo 块 + 行列表 + 底部三条链 + 灰脚注，全部照参考稿那张关于页的结构。
 *
 * 使用提示在首页顶栏的帮助里。隐私政策、用户协议、使用统计走底部文字链：
 * 它们是「偶尔要看一眼」的参考材料，不该在行列表里和「项目主页」抢一个位置，
 * 而移动端把这类条款链放在页面最末尾本身就是惯例。
 *
 * 第三方许可不再在这页铺开：它有自己的列表页，图标集也在里面。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenStats: () -> Unit
) {
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

            Column {
                SettingGroup(title = stringResource(R.string.about_service_group)) {
                    SettingRow(
                        title = stringResource(R.string.about_project),
                        icon = Globe.Outline,
                        tail = RowTail.Next,
                        onClick = { context.openWebPage(PROJECT_URL) }
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

                // 没配后台地址的构建不联网：那条链点进去是一页「本机不会发任何东西」的说明。
                val statsVisible = HubRules.enabled(BuildConfig.HUB_BASE_URL)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FooterLink(stringResource(R.string.privacy_title), onOpenPrivacy)
                    LinkDot()
                    FooterLink(stringResource(R.string.terms_title), onOpenTerms)
                    if (statsVisible) {
                        LinkDot()
                        FooterLink(stringResource(R.string.stats_title), onOpenStats)
                    }
                }
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

/** 底部条款链：灰字、无边框、整段可点。和脚注拉开颜色一档就够了，不需要蓝色来提示「这是链接」。 */
@Composable
private fun FooterLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
private fun LinkDot() {
    Text(
        "·",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline
    )
}
