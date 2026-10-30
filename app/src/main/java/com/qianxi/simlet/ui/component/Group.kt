package com.qianxi.simlet.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qianxi.simlet.R
import com.qianxi.simlet.ui.theme.SimletCardShape
import com.qianxi.simlet.ui.theme.SimletInnerShape
import dev.reicon.ChevronExpandY
import dev.reicon.ChevronRight

/** 参考稿实测的行高与内缩：行 56dp，图标距卡片 20dp，图标到文字 16dp，箭头距卡片 24dp。 */
private val RowHeight = 56.dp

/** 卡片内容列的左右内缩；不带图标的多行条目（许可列表那种）也按这一套对齐。 */
val RowStart = 20.dp
val RowEnd = 24.dp
private val IconGap = 16.dp
private val IconSize = 20.dp

/** 文字列的左缘 = 图标内缩 + 图标 + 间距；分割线、无图标的行、卡内正文都对齐到这里。 */
val TextStart = 56.dp

/** 行尾箭头的三种形态。参考稿里跳转是 `›`，弹出选择器是上下箭头。 */
enum class RowTail { None, Next, Picker }

/**
 * 分组：标题在卡片**外面**，卡片内不再有标题行。
 *
 * 实测组标题与卡片内图标的左缘对齐（距卡片 20dp），字号与行标签相同，只靠颜色区分；
 * 组间距 64dp 里含组标题自身的高度。
 */
@Composable
fun SettingGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = RowStart, bottom = 8.dp)
            )
        }
        Surface(
            shape = SimletCardShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(content = content)
        }
    }
}

/**
 * 分割线：左端与文字对齐（不贯穿图标），右端贴卡片边。
 *
 * 实测左内缩 56dp = 图标 20 + 图标 20 + 间距 16；粗细是 0.5dp 的发丝线，
 * 用 1dp 会在浅色下明显偏重。没有图标列的列表传 [start] = 0。
 */
@Composable
fun RowDivider(start: Dp = TextStart) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = start)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * 通用设置行：图标 + 标题 + 尾值 + 箭头。
 *
 * 参考稿的每一行都是这个结构，所以不再为「跳转」「选择」各写一个组件——
 * 它们的差别只有 [tail] 和标题右侧那点尾值。
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    value: String? = null,
    valueColor: Color? = null,
    badge: String? = null,
    tail: RowTail = RowTail.None,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val contentAlpha = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .height(RowHeight)
            .padding(start = RowStart, end = RowEnd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(IconGap))
        } else {
            // 无图标的行也要把文字推到 TextStart，否则同一张卡里出现两种左缘。
            Spacer(Modifier.width(IconSize + IconGap))
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else contentAlpha,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            Spacer(Modifier.width(10.dp))
            TextBadge(badge)
        }
        if (value != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }
        trailing?.invoke()
        if (tail != RowTail.None) {
            Spacer(Modifier.width(6.dp))
            Icon(
                if (tail == RowTail.Next) ChevronRight.Outline else ChevronExpandY.Outline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/** 「体验版」那种小徽章：绿底绿字，圆角 5dp。整套 UI 唯一的彩色用在这里。 */
@Composable
fun TextBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = SimletInnerShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

/**
 * 纯文字弹层：使用提示、权限与数据说明这类「读一段话」的内容用它。
 *
 * 参考稿把这类长文本从主页面里挪了出去，页面上只留一行入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(
    visible: Boolean,
    title: String,
    body: String,
    onDismiss: () -> Unit
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 选项弹层：明暗档位、语言这类少量互斥选项用它，
 * 取代原先摆在行右侧的三个内联 chip——参考稿的对应位置只有一个尾值加上下箭头。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSheet(
    visible: Boolean,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            options.forEachIndexed { index, option ->
                val active = index == selectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(index)
                            onDismiss()
                        }
                        .padding(vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (active) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (active) {
                        Text(
                            stringResource(R.string.badge_current),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                if (index != options.lastIndex) RowDivider(start = 0.dp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
