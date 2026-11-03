package com.qianxi.simlet.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qianxi.simlet.R
import com.qianxi.simlet.ui.component.RowDivider
import com.qianxi.simlet.ui.component.SettingGroup
import com.qianxi.simlet.ui.component.StatsRow

/**
 * 条款页：隐私政策、用户协议、使用统计共用这一个骨架。
 *
 * 三段文本的形状完全一样（小标题 + 正文若干段），差别只在内容，所以内容用
 * `Section` 列出来而不是各写一屏——将来加一节只需要加一条，不会顺手改出第三种版式。
 *
 * 正文走 `onSurfaceVariant`：这类页面是「读一遍就走」的参考材料，不该和主操作同权重。
 */
@Composable
private fun ArticleScreen(
    title: String,
    sections: List<Pair<Int, Int>>,
    onBack: () -> Unit,
    tail: @Composable (() -> Unit)? = null
) {
    AppScaffold(title = title, onBack = onBack) { insets ->
        AppColumn(insets) {
            SettingGroup {
                sections.forEachIndexed { index, (headingRes, bodyRes) ->
                    if (index > 0) RowDivider(start = 0.dp)
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text(
                            stringResource(headingRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(bodyRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.25f
                        )
                    }
                }
            }
            tail?.invoke()
        }
    }
}

/**
 * 隐私政策。
 *
 * 只写结论，不写论证：这类页面是给用户五分钟扫一遍的，「我们为什么这样设计」属于开发者文档。
 * 上一版里那句「早期版本说过不联网」也搬走了——那是版本史，不是政策。
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    ArticleScreen(
        title = stringResource(R.string.privacy_title),
        sections = listOf(
            R.string.privacy_h1 to R.string.privacy_b1,
            R.string.privacy_h2 to R.string.privacy_b2,
            R.string.privacy_h3 to R.string.privacy_b3,
            R.string.privacy_h4 to R.string.privacy_b4,
            R.string.privacy_h5 to R.string.privacy_b5
        ),
        onBack = onBack
    )
}

/** 用户协议：用途、风险、免责三件是这套工具类应用必须说清的，其余一句带过。 */
@Composable
fun TermsScreen(onBack: () -> Unit) {
    ArticleScreen(
        title = stringResource(R.string.terms_title),
        sections = listOf(
            R.string.terms_h1 to R.string.terms_b1,
            R.string.terms_h2 to R.string.terms_b2,
            R.string.terms_h3 to R.string.terms_b3,
            R.string.terms_h4 to R.string.terms_b4,
            R.string.terms_h5 to R.string.terms_b5,
            R.string.terms_h6 to R.string.terms_b6,
            R.string.terms_h7 to R.string.terms_b7
        ),
        onBack = onBack
    )
}

/**
 * 使用统计：说明与开关同一页。
 *
 * 开关全站只在这一页出现一次——摆在关于页、设置页和这里三处，等于同一件事有三个地方能关，
 * 而人只会记住最麻烦的那一个。
 */
@Composable
fun StatsScreen(onBack: () -> Unit, statsOn: Boolean, onToggle: (Boolean) -> Unit) {
    ArticleScreen(
        title = stringResource(R.string.stats_title),
        sections = listOf(
            R.string.stats_h1 to R.string.stats_b1,
            R.string.stats_h2 to R.string.stats_b2,
            R.string.stats_h3 to R.string.stats_b3,
            R.string.stats_h4 to R.string.stats_b4,
            R.string.stats_h5 to R.string.stats_b5
        ),
        onBack = onBack,
        tail = {
            SettingGroup {
                StatsRow(on = statsOn, onToggle = onToggle)
            }
        }
    )
}
