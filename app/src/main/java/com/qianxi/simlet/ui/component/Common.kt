package com.qianxi.simlet.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qianxi.simlet.R
import com.qianxi.simlet.model.SimSlot
import com.qianxi.simlet.ui.operatorName
import com.qianxi.simlet.ui.theme.SimletBadgeShape
import com.qianxi.simlet.ui.theme.SimletChipShape
import dev.reicon.Sim

/** 读取中占位，避免界面瞬间空白跳动。 */
@Composable
fun SkeletonBlock(lines: Int = 2) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        repeat(lines) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index == lines - 1) 0.55f else 1f)
                    .height(15.dp)
                    .clip(SimletBadgeShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

/**
 * 左标签右取值的一行，用在环境自检那种「读数」场景（没有图标，所以不走 SettingRow）。
 *
 * 左缘仍然落在 [TextStart]：同一张卡里可以和带图标的 SettingRow 混排而不错列。
 */
@Composable
fun LabeledValue(label: String, value: String, emphasized: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = TextStart, end = 24.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
            color = if (emphasized) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 状态圆点。
 *
 * 参考稿没有胶囊状的徽章组件，行尾只留一个色点表达「就绪 / 未就绪」，
 * 文字本身已经写了状态，点只是让它扫一眼能定位。
 */
@Composable
fun StatusDot(ready: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(
                if (ready) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.outlineVariant
            )
    )
}

/**
 * 双卡选择：参考稿没有并排卡这种版式，信息单元就是「卡片 + 行 + 分割线」，
 * 所以两张卡摊成两行，选中行用绿点标出，国家码与覆盖状态收进尾值。
 */
@Composable
fun SlotRows(
    slots: List<SimSlot>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column {
        slots.forEachIndexed { index, slot ->
            if (index > 0) RowDivider()
            val effective = (slot.effectiveCountryIso ?: slot.applied.countryIso)?.uppercase()
            SettingRow(
                title = stringResource(R.string.sim_row, index + 1,
                    slot.applied.carrierName ?: operatorName(slot.reportedOperator)),
                icon = Sim.Outline,
                // 未覆盖时只报国家码：「未覆盖」是负向信息，绿点与尾值已经说明没动过；
                // 写全「CN · No override」在英文下会把卡名挤成 "Chin…"。
                value = effective?.let {
                    if (slot.overridden) "$it · ${stringResource(R.string.override_state_on)}" else it
                },
                valueColor = if (index == selectedIndex) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onSelect(index) },
                trailing = { StatusDot(ready = index == selectedIndex, modifier = Modifier.padding(start = 10.dp)) }
            )
        }
    }
}

/** 一行式选择器：整行可点进弹层，右侧尾值未选时给占位说法。 */
@Composable
fun PickerRow(
    label: String,
    value: String?,
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    SettingRow(
        title = label,
        icon = icon,
        value = value?.takeIf { it.isNotBlank() } ?: placeholder,
        valueColor = if (value.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface,
        tail = RowTail.Next,
        onClick = onClick,
        modifier = modifier
    )
}

/** chip：选中态用黑底反白，参考稿没有实心彩色按钮。 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(SimletChipShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
