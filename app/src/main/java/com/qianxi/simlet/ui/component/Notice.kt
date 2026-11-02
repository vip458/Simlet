package com.qianxi.simlet.ui.component

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.qianxi.simlet.R
import com.qianxi.simlet.remote.Notice
import com.qianxi.simlet.ui.openWebPage
import dev.reicon.Globe
import dev.reicon.InfoCircle

/**
 * 公告分组。
 *
 * 摆在首页最后一组，不给它抢主操作的位置：一条公告不该把「保存」按钮顶出屏幕
 * （检查更新那组踩过这个坑，见 UpdateSection 里对发布说明长度的处理）。
 *
 * 后台没发、或者这个构建根本没配后台地址时，整组不出现，页面不留空卡。
 */
@Composable
fun NoticeSection(notice: Notice, onOpen: () -> Unit) {
    SettingGroup(title = stringResource(R.string.notice_group)) {
        SettingRow(
            title = notice.title,
            icon = InfoCircle.Outline,
            // 普通级别不给徽章：这套 UI 唯一的彩色位就在徽章上，一条常规公告不该带颜色。
            badge = when (notice.level) {
                "warn" -> stringResource(R.string.notice_level_warn)
                "alert" -> stringResource(R.string.notice_level_alert)
                else -> null
            },
            tail = RowTail.Next,
            onClick = onOpen
        )
    }
}

/**
 * 公告正文。
 *
 * 有链接才多出「查看详情」一行，点了交给系统浏览器——不在应用内渲染网页，
 * 那就少一个 WebView 依赖，也少一类把公告当成网址钓鱼面的可能。
 * 正文为空时（只发了标题的公告）下面就是空的，不编一句「暂无内容」占位。
 */
@Composable
fun NoticeSheet(visible: Boolean, notice: Notice?, onDismiss: () -> Unit) {
    if (!visible || notice == null) return
    val context = LocalContext.current
    val link = notice.url.takeIf { it.isNotBlank() }
    InfoSheet(
        visible = true,
        title = notice.title,
        body = notice.body,
        onDismiss = onDismiss,
        linkText = link?.let { stringResource(R.string.notice_open_link) },
        onLink = link?.let { target -> { context.openWebPage(target) } }
    )
}

/**
 * 「共享匿名使用统计」这一行。
 *
 * 只在构建时配了后台地址时才出现：一个控制不了任何行为的开关比没有开关更糟，
 * 而自构建和 fork 默认就是留空，所以这一行对它们根本不存在。
 *
 * 整行可点，右侧的开关只是同一件事的另一种点法——56dp 的行里只让人去瞄一个 40dp 的开关不合理。
 * 尾值不放「已开启/已关闭」：开关本身已经说了这件事，说两遍是噪音。
 */
@Composable
fun StatsRow(on: Boolean, onToggle: (Boolean) -> Unit) {
    SettingRow(
        title = stringResource(R.string.settings_stats),
        icon = Globe.Outline,
        trailing = { Switch(checked = on, onCheckedChange = onToggle) },
        onClick = { onToggle(!on) }
    )
}
