package com.qianxi.simlet.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.qianxi.simlet.ui.theme.SimletButtonShape
import com.qianxi.simlet.ui.theme.SimletInnerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qianxi.simlet.R
import com.qianxi.simlet.data.OperatorCatalog
import com.qianxi.simlet.data.RegionCatalog
import com.qianxi.simlet.ui.englishUi
import dev.reicon.Search

/** 全部国家/地区 + 自定义输入。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit
) {
    if (!visible) return
    val english = englishUi()
    var keyword by rememberSaveable { mutableStateOf("") }
    val results = remember(keyword, english) {
        RegionCatalog.all.filter { it.matches(keyword) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.sheet_region_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = { Text(stringResource(R.string.sheet_region_search)) },
                leadingIcon = { Icon(Search.Outline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = SimletInnerShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            CustomRegionField(onApply = { onPick(it); onDismiss() })
            Spacer(Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .navigationBarsPadding()
            ) {
                items(results, key = { it.iso }) { region ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(region.iso)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            region.displayName(english),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            region.iso.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 自定义输入格：输入框 + 实心「应用」，两个弹层共用。
 *
 * 两处改动都是针对原来的毛病：
 * 提示原来摆在框外、和「应用」挤一行，11sp 灰字看不清；现在放进框的内端，
 * 整块高度从 64dp 降到输入框默认的 56dp，也不再需要额外一行。
 * 「应用」原来是无边框文字按钮，看不出可点；现在是标准按钮，禁用态直接灰掉。
 */
@Composable
private fun CustomApplyField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    hint: String?,
    hintEmphasized: Boolean,
    enabled: Boolean,
    onApply: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            trailingIcon = hint?.let { text ->
                {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        color = if (hintEmphasized) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            },
            singleLine = true,
            shape = SimletInnerShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = onApply,
            enabled = enabled,
            shape = SimletButtonShape,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            modifier = Modifier.height(56.dp)
        ) {
            Text(stringResource(R.string.sheet_apply))
        }
    }
}

@Composable
private fun CustomRegionField(onApply: (String?) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val normalized = RegionCatalog.normalize(draft)

    CustomApplyField(
        value = draft,
        onValueChange = { draft = it },
        placeholder = stringResource(R.string.sheet_custom_code),
        hint = normalized?.let { stringResource(R.string.sheet_write_code, it) }
            ?: stringResource(R.string.sheet_two_letters),
        hintEmphasized = normalized != null,
        enabled = normalized != null,
        onApply = { normalized?.let(onApply) }
    )
}

/** 运营商名称没有格式要求，所以那一格不给提示。 */
@Composable
private fun CustomOperatorField(onApply: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val value = draft.trim()

    CustomApplyField(
        value = draft,
        onValueChange = { draft = it },
        placeholder = stringResource(R.string.sheet_custom_name),
        hint = null,
        hintEmphasized = false,
        enabled = value.isNotEmpty(),
        onApply = { onApply(value) }
    )
}

/**
 * 运营商预设：按国家分组，可折叠；选中目标国家码时可直接过滤到该国。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorSheet(
    visible: Boolean,
    currentIso: String?,
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    if (!visible) return
    val english = englishUi()
    var keyword by rememberSaveable { mutableStateOf("") }
    var onlyCurrent by rememberSaveable { mutableStateOf(false) }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }

    val groups = remember(keyword, onlyCurrent, currentIso, english) {
        OperatorCatalog.search(keyword)
            .let { source ->
                if (onlyCurrent && currentIso != null) {
                    source.filter { it.region.iso == currentIso }
                } else {
                    source
                }
            }
    }

    // 搜到精确同名预设时不再另列「自定义」，但自定义入口本身是常驻的一格输入框，
    // 不跟着搜索词出现或消失——原来那种「搜到什么才能写什么」的藏法没人找得到。
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.sheet_operator_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = { Text(stringResource(R.string.sheet_operator_search)) },
                leadingIcon = { Icon(Search.Outline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = SimletInnerShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            CustomOperatorField(onApply = { onPick(it); onDismiss() })
            Spacer(Modifier.height(6.dp))

            if (currentIso != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onlyCurrent = !onlyCurrent }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(checked = onlyCurrent, onCheckedChange = { onlyCurrent = it })
                    Text(
                        stringResource(
                            R.string.sheet_only_region,
                            RegionCatalog.labelOf(currentIso, english) ?: currentIso.uppercase()
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text(
                    stringResource(R.string.sheet_no_region_hint, OperatorCatalog.allOperators.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .navigationBarsPadding()
            ) {
                item(key = "none") {
                    SheetActionRow(
                        text = stringResource(R.string.keep_current),
                        selected = selected.isNullOrBlank(),
                        onClick = {
                            onPick("")
                            onDismiss()
                        }
                    )
                }
                groups.forEach { group ->
                    item(key = "header-${group.region.iso}") {
                        val expanded = collapsed[group.region.iso] != true
                        GroupHeader(
                            text = group.region.displayName(english),
                            count = group.operators.size,
                            expanded = expanded,
                            onClick = { collapsed[group.region.iso] = expanded }
                        )
                    }
                    if (collapsed[group.region.iso] != true) {
                        items(group.operators, key = { "${group.region.iso}-${it.value}" }) { operator ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPick(operator.value)
                                        onDismiss()
                                    }
                                    .padding(vertical = 11.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val display = operator.displayName(english)
                                    Text(
                                        display,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (operator.value == selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    // 英文界面下展示名就是写入值，再列一遍只会重复。
                                    if (display != operator.value) {
                                        Text(
                                            operator.value,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (operator.value == selected) CurrentBadge()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 弹层顶部的动作行：不修改。 */
@Composable
private fun SheetActionRow(
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) CurrentBadge()
    }
}

@Composable
private fun CurrentBadge() {
    Text(
        stringResource(R.string.badge_current),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

/** 使用提示：从关于页搬进首页顶栏的帮助按钮，就地看，不用为了几条提示跳去另一个页面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.help_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.help_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
