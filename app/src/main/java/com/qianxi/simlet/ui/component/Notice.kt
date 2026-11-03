package com.qianxi.simlet.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qianxi.simlet.R
import com.qianxi.simlet.remote.Notice
import com.qianxi.simlet.ui.openWebPage
import dev.reicon.Globe
import dev.reicon.InfoCircle

/**
 * 公告分组。
 *
 * 摆在首页第一组：一条藏在最后一组的公告等于没有公告，发它就是为了让人看见。
 * 「不该把保存按钮顶出屏幕」这个顾虑不成立——保存与还原在底部栏里，不随内容滚动。
 *
 * 后台没发、或者这个构建根本没配后台地址时，整组不出现，页面不留空卡。
 */
@Composable
fun NoticeSection(notice: Notice, unread: Boolean, onOpen: () -> Unit) {
    SettingGroup(title = stringResource(R.string.notice_group)) {
        SettingRow(
            title = notice.title,
            icon = InfoCircle.Outline,
            // 普通级别不给徽章：彩色位一次只承载一个信号，红点已经占了。
            badge = when (notice.level) {
                "warn" -> stringResource(R.string.notice_level_warn)
                "alert" -> stringResource(R.string.notice_level_alert)
                else -> null
            },
            trailing = if (unread) ({ UnreadDot() }) else null,
            tail = RowTail.Next,
            onClick = onOpen
        )
    }
}

/**
 * 未读点。
 *
 * 用 `error` 那一档红（浅色 #B3261E、深色 #FFB4AB），是工程里现成的色值，不新增颜色。
 * 直径 7dp：再小就成了噪点，再大就压过正文。
 */
@Composable
fun UnreadDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(7.dp)
            .background(MaterialTheme.colorScheme.error, CircleShape)
    )
}

/**
 * 公告正文。
 *
 * 有链接才多出「查看详情」那颗主按钮，点了交给系统浏览器——不在应用内渲染网页，
 * 那就少一个 WebView 依赖，也少一类把公告当成网址钓鱼面的可能。
 * 正文为空时（只发了标题的公告）下面就是空的，不编一句「暂无内容」占位。
 */
@Composable
fun NoticeDialog(visible: Boolean, notice: Notice?, onDismiss: () -> Unit) {
    if (!visible || notice == null) return
    val context = LocalContext.current
    val link = notice.url.takeIf { it.isNotBlank() }
    InfoDialog(
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
 * 只在「使用统计」页出现：开关要真能关掉一条网络链路才有意义，而自构建和 fork 默认留空，
 * 那一页对它们根本不存在。
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
