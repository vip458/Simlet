package com.qianxi.simlet.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qianxi.simlet.R
import com.qianxi.simlet.remote.Notice
import com.qianxi.simlet.remote.RemoteStore
import com.qianxi.simlet.ui.openWebPage
import dev.reicon.Bullhorn
import dev.reicon.Globe

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
            icon = Bullhorn.Outline,
            // 日期占右侧那一列，和 SIM 行的「CN」同一个位置、同一个字号：
            // 内容不写日期的公告，事后翻出来不知道是哪天发的。
            value = notice.dateText(),
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
        // 弹窗是「这一条公告」的完整交代，日期必须在这里也能看到：
        // 内容不写日期的话，翻出来一条根本不知道是哪天发的。
        meta = notice.dateText().takeIf { it.isNotBlank() },
        onDismiss = onDismiss,
        linkText = link?.let { stringResource(R.string.notice_open_link) },
        onLink = link?.let { target -> { context.openWebPage(target) } }
    )
}

/**
 * 公告位：首页那一整块（分组行 + 刚推送那一次的自动弹窗）。
 *
 * 只挂首页。以前环境自检页也挂着一份，理由是「停在那页的正是还没授权的用户，
 * Shizuku 打不开怎么办这类话就是说给他们听的」——理由不假，但授权还没成
 * 就把一条和授权无关的公告弹到人脸上，等于在最要紧的那一步中间插了个岔，所以撤了。
 * 真要发「某个机型这版别用」，用户授权完进首页第一眼就是它。
 */
@Composable
fun NoticeSlot(remote: RemoteStore, onOpenNotices: () -> Unit) {
    var dialogOpen by remember { mutableStateOf(false) }
    val notice = remote.notice
    // 「打扰一次」的名额在这里被弹窗取走：取走的同时就把这条记 down，杀进程重来不会再弹。
    // 通知栏抢的是同一个名额，规则见 RemoteStore.takeNoticeAttention。
    LaunchedEffect(notice?.id) {
        if (remote.takeNoticeAttention(notice) != null) dialogOpen = true
    }
    notice?.let { item ->
        NoticeSection(
            notice = item,
            unread = remote.noticeUnread(item),
            // 点这一行进的是公告列表，不再是弹窗：弹窗只留给「刚推送那一次」。
            onOpen = {
                onOpenNotices()
                remote.markNoticeSeen(item.id)
            }
        )
    }
    NoticeDialog(visible = dialogOpen, notice = notice, onDismiss = { dialogOpen = false })
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
