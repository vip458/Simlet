package com.qianxi.simlet.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qianxi.simlet.R
import com.qianxi.simlet.ui.theme.SimletButtonShape
import dev.reicon.Xmark
import kotlinx.coroutines.delay

/**
 * 全站唯一的居中弹窗容器。公告、选项、帮助、发现新版本都从这里出，
 * 为的是同一套动画、同一个关闭路径、同一个尺寸档——弹窗每页各画一遍的话，
 * 差别会以「哪里怪怪的」的形式被感觉到，而没人能指出是哪一处。
 *
 * 三条规定：
 * - **不加阴影**。这套 UI 通篇无阴影，弹层靠「底色比页面亮一档 + 1dp 描边」浮起来；
 * - **正文区自己滚**。窗高封顶屏高 74%，长文本把最后一句裁掉是最坏的结果——
 *   而公告被裁掉的恰好是「怎么关掉」那半句；
 * - **关闭键、点遮罩、系统返回键走同一个回调**，行为不许有差别。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimletDialog(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    dismissText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // 退场动画要跑完才能拆窗：跟着 visible 一起消失的话只有进没有出。
    val shown = remember { MutableTransitionState(false) }
    LaunchedEffect(visible) {
        if (visible) {
            shown.targetState = true
        } else if (shown.targetState) {
            shown.targetState = false
            delay(EXIT_MS)
        }
    }
    if (!visible && !shown.currentState) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(tween(120)) + scaleIn(tween(180, easing = FastOutSlowInEasing), initialScale = 0.96f),
            exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.98f)
        ) {
            BoxWithConstraints {
                Surface(
                    shape = DialogShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .widthIn(max = 420.dp)
                        .heightIn(max = maxHeight * 0.74f)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismiss) {
                                // 关闭键走 outline 灰：它是控件不是信息，按图标规范不该和正文同重。
                                Icon(
                                    Xmark.Outline,
                                    contentDescription = stringResource(R.string.dialog_close),
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        DialogDivider()
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            content = content
                        )
                        if (confirmText != null || secondaryText != null || dismissText != null) {
                            DialogDivider()
                            // FlowRow 而不是 Row：三颗按钮（稍后再说 / 通过网盘下载 / 去下载）在 375dp 屏上
                            // 一行放不下，硬挤会把主按钮的文字压成一列竖字。放不下就换行，永远右对齐。
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.End,
                                maxLines = 2
                            ) {
                                if (dismissText != null) {
                                    TextButton(onClick = onDismiss) { Text(dismissText) }
                                }
                                if (secondaryText != null && onSecondary != null) {
                                    TextButton(onClick = onSecondary) { Text(secondaryText) }
                                }
                                if (confirmText != null && onConfirm != null) {
                                    Button(
                                        onClick = onConfirm,
                                        shape = SimletButtonShape,
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                    ) { Text(confirmText) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * 纯文字弹窗：公告、使用提示这类「读一段话」的内容用它。
 *
 * [linkText] + [onLink] 是给公告那种「正文下面还挂一个动作」留的位；
 * 挂上动作时它就成为底部那颗主按钮，而不是正文里的一行蓝字。
 * 没挂动作时底部只有一颗关闭，[dismissText] 用来换它的措辞（默认「关闭」）。
 */
@Composable
fun InfoDialog(
    visible: Boolean,
    title: String,
    body: String,
    onDismiss: () -> Unit,
    linkText: String? = null,
    onLink: (() -> Unit)? = null,
    dismissText: String? = null
) {
    val hasLink = linkText != null && onLink != null
    SimletDialog(
        visible = visible,
        title = title,
        onDismiss = onDismiss,
        confirmText = if (hasLink) linkText else null,
        onConfirm = onLink,
        dismissText = if (hasLink) null else dismissText ?: stringResource(R.string.dialog_close)
    ) {
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 选项弹窗：明暗档位、语言这类少量互斥选项。
 *
 * 点一下即选即关，不留「确定」：多一步确认只是让人多点一次，而且会让人怀疑刚才到底选了没有。
 * 当前项用文字徽章而不是勾，和这套 UI 其它地方一致——弹层里的「当前」从来不是图标。
 */
@Composable
fun OptionDialog(
    visible: Boolean,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    SimletDialog(visible = visible, title = title, onDismiss = onDismiss) {
        options.forEachIndexed { index, option ->
            if (index > 0) RowDivider(start = 0.dp)
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
                    color = if (index == selectedIndex) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (index == selectedIndex) {
                    Text(
                        stringResource(R.string.badge_current),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

private val DialogShape = RoundedCornerShape(16.dp)

/** 比退场动画（120ms 淡出 + 140ms 缩放）再宽一点，留出不小的余量。 */
private const val EXIT_MS = 300L
