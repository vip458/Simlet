package com.qianxi.simlet.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.qianxi.simlet.BuildConfig
import com.qianxi.simlet.R
import com.qianxi.simlet.remote.HubClient
import com.qianxi.simlet.remote.HubRules
import com.qianxi.simlet.remote.Notice
import com.qianxi.simlet.remote.NoticesOutcome
import com.qianxi.simlet.remote.RemoteStore
import com.qianxi.simlet.ui.UiPrefs
import com.qianxi.simlet.ui.component.NoticeDialog
import com.qianxi.simlet.ui.component.RowDivider
import com.qianxi.simlet.ui.component.RowTail
import com.qianxi.simlet.ui.component.SettingGroup
import com.qianxi.simlet.ui.component.SettingRow
import dev.reicon.Bell
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 公告列表：后台发过的每一条都在这里，已经下线的也照样列出来。
 *
 * 入口只有一个——首页那条公告。首页不再额外加「全部公告」那一行：同一屏里两条入口指向
 * 同一个东西，读起来像「有两个公告要看」，而实际只有一个。
 *
 * 正文仍然交给 [NoticeDialog]：弹窗是全站唯一的公告正文容器，列表里再造一套就等于
 * 同一篇文字有两种排版、两处滚动规则。
 */
@Composable
fun NoticesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val remote = RemoteStore.of(context)
    // 先拿缓存里的那一条顶着：拉列表要一个来回，空屏会让人以为点错了。
    var items by remember { mutableStateOf(remote.notices ?: listOfNotNull(remote.notice)) }
    var selected by remember { mutableStateOf<Notice?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!HubRules.enabled(BuildConfig.HUB_BASE_URL)) return@LaunchedEffect
        val tag = UiPrefs.of(context).localeTag ?: Locale.getDefault().language
        val outcome = withContext(Dispatchers.IO) {
            HubClient.fetchNotices(remote.noticesEtag, HubRules.queryLang(tag))
        }
        when (outcome) {
            is NoticesOutcome.Updated -> {
                remote.applyNotices(outcome.items, outcome.etag)
                items = outcome.items
                failed = false
            }
            // 304 的意思就是「内存里这份还是最新的」，什么都不用改。
            NoticesOutcome.Cached -> failed = false
            NoticesOutcome.Unreachable -> failed = true
        }
    }

    AppScaffold(title = stringResource(R.string.notices_title), onBack = onBack) { insets ->
        AppColumn(insets) {
            if (items.isEmpty()) {
                SettingGroup { HintLine(stringResource(R.string.notices_empty)) }
            } else {
                SettingGroup {
                    items.forEachIndexed { index, notice ->
                        if (index > 0) RowDivider(start = 0.dp)
                        SettingRow(
                            title = notice.title,
                            icon = Bell.Outline,
                            value = notice.dateLabel(),
                            // 彩色位一次只承载一个信号：级别在首页那行已经给过徽章了，
                            // 列表里只标「这条已经撤下来了」，不再重复上级别色。
                            badge = if (!notice.live) stringResource(R.string.notices_offline) else null,
                            tail = RowTail.Next,
                            onClick = { selected = notice }
                        )
                    }
                }
            }
            // 拉失败时不顶掉已有内容，只在底下补一行：缓存的那条照样能读，
            // 报一个全屏错误会把「能看到的东西」也一起藏起来。
            if (failed) {
                SettingGroup { HintLine(stringResource(R.string.notices_failed)) }
            }
        }
    }

    NoticeDialog(visible = selected != null, notice = selected, onDismiss = { selected = null })
}

/** 组内的一行说明文字，用于空态和失败态。 */
@Composable
private fun HintLine(text: String) {
    Column(Modifier.padding(20.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 列表右侧那一个日期。
 *
 * 当年只写月-日：一屏里全是「11-03」比全是「2026-11-03」好读，而年份是这一屏的共同前提。
 * 跨年才把年份带回来，否则翻到去年那条会读成「还没发生的 11-03」。
 */
private fun Notice.dateLabel(): String {
    if (pubMs <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = pubMs }
    val pattern = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) "MM-dd" else "yyyy-MM-dd"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(pubMs))
}
