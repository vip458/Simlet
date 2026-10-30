package com.qianxi.simlet.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qianxi.simlet.R
import com.qianxi.simlet.data.RegionCatalog
import com.qianxi.simlet.ui.theme.SimletChipShape
import dev.reicon.ChevronDown
import dev.reicon.ChevronUp

/** 一格 chip 的内容与点击。 */
private class ChipCell(val text: String, val selected: Boolean, val onClick: () -> Unit)

/**
 * 国家码快捷选择：chip 等宽铺满整行，选中态由高亮表达。
 * 当前写入值与国家名由卡片标题右侧的一行小字承担，不再重复展示大字。
 *
 * 列数是按可用宽度**算**出来的，不是写死的：原来固定 52dp，在 375dp 与 369dp
 * 两台测试机上每行末尾都剩 30dp 上下的空白，屏越宽越明显。
 * 行也是自己切的，没有用 FlowRow——FlowRow 按取整后的像素判断放不放得下，
 * 4 列时差一两个像素就会掉成 3 列，留白又回来了。
 *
 * @param selected 当前选择的小写 ISO；null 表示不改国家码
 */
@Composable
fun IsoPicker(
    selected: String?,
    onPick: (String?) -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val customSelected = selected != null && RegionCatalog.labelOf(selected) == null
    val cells = buildList {
        RegionCatalog.all.filter { it.common }.forEach { region ->
            add(ChipCell(region.iso.uppercase(), selected == region.iso) { onPick(region.iso) })
        }
        add(ChipCell(stringResource(R.string.chip_keep), selected == null) { onPick(null) })
        add(
            ChipCell(
                stringResource(if (customSelected) R.string.chip_custom else R.string.chip_more),
                customSelected,
                onOpenAll
            )
        )
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 8.dp
        val columns = if (maxWidth != Dp.Infinity) {
            ((maxWidth + gap) / (60.dp + gap)).toInt().coerceIn(4, 8)
        } else {
            4
        }

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            cells.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    row.forEach { cell ->
                        CodeChip(cell.text, cell.selected, Modifier.weight(1f), cell.onClick)
                    }
                    // 最后一行不满时补空列，chip 宽度才和上面几行一致。
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CodeChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(SimletChipShape)
            .background(
                // 参考稿没有描边控件：未选中靠比卡片深一档的灰底，选中直接黑底反白。
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 分区标题，用于底部弹层里的可折叠分组。 */
@Composable
fun GroupHeader(text: String, count: Int?, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Text(
                pluralStringResource(R.plurals.count_items, count, count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            imageVector = if (expanded) ChevronUp.Outline else ChevronDown.Outline,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.outline
        )
    }
}
