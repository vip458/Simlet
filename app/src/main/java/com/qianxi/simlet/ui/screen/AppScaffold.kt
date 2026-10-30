package com.qianxi.simlet.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qianxi.simlet.R
import androidx.compose.foundation.background
import dev.reicon.ArrowLeft

/** 顶栏高度与返回键底衬：实测圆衬 117px ≈ 40dp，左内缩 46px ≈ 16dp。 */
private val BarHeight = 56.dp

/** 内容列的左缘：卡片内缩与无返回键时的标题都落在这里。实测卡片距屏幕边缘 65px。 */
private val ContentInset = 24.dp

/**
 * 各屏共用的外框。
 *
 * 标题的摆法跟参考稿一致，但只在**左右各有一组控件**时才居中：二级页左边是返回键、
 * 右边是动作键，居中成立。首页没有返回键，右侧三个图标会把视觉重心整个拽到右边，
 * 居中的标题就贴到了图标脸上——所以无返回键时标题落到内容列（24dp）左对齐。
 */
@Composable
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(BarHeight)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = if (onBack == null) TextAlign.Start else TextAlign.Center,
                    maxLines = 1,
                    modifier = if (onBack == null) {
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = ContentInset, end = 200.dp)
                    } else {
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 96.dp)
                    }
                )
                if (onBack != null) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    ArrowLeft.Outline,
                                    contentDescription = stringResource(R.string.action_back),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost
    ) { insets -> content(insets) }
}

/**
 * 卡片列表容器：左右内缩 [ContentInset]。
 *
 * 组间距实测 63dp，其中组标题自身占掉约 28dp，所以列间距给 34dp，两者相加才是参考稿的空档。
 */
@Composable
fun AppColumn(insets: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(insets)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ContentInset, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(34.dp),
        content = content
    )
}
