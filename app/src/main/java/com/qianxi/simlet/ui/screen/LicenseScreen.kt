package com.qianxi.simlet.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qianxi.simlet.R
import com.qianxi.simlet.licenses.openSourceEntries
import com.qianxi.simlet.ui.component.RowDivider
import com.qianxi.simlet.ui.component.RowEnd
import com.qianxi.simlet.ui.component.RowStart
import com.qianxi.simlet.ui.component.SettingGroup
import com.qianxi.simlet.ui.component.TextBadge
import com.qianxi.simlet.ui.openWebPage

/**
 * 开源许可页：一条目一行，整行可点，跳到该项目地址。
 *
 * 版式照系统那张「开放源代码许可」列表——名称、作者、许可证徽章、右侧版本——
 * 但配色、圆角、分割线全部走本项目主题：徽章是绿底绿字而不是参考图的青色，
 * 卡片仍是 12dp 无阴影。图标集不另立分类，和依赖混排在一起。
 */
@Composable
fun LicenseScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    AppScaffold(title = stringResource(R.string.about_license), onBack = onBack) { insets ->
        AppColumn(insets) {
            SettingGroup {
                openSourceEntries.forEachIndexed { index, entry ->
                    // 这一屏的条目没有图标列，分割线要贴着文字而不是让出 56dp。
                    if (index > 0) RowDivider(start = RowStart)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { context.openWebPage(entry.url) }
                            .padding(start = RowStart, end = RowEnd, top = 12.dp, bottom = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                entry.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                entry.version,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            entry.author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        TextBadge(entry.license)
                    }
                }
            }
        }
    }
}
