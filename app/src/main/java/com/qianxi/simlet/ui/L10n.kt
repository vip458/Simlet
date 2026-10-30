package com.qianxi.simlet.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.qianxi.simlet.R

/**
 * 界面语言判定与「数据层名称」的本地化入口。
 *
 * 只出中英两份词条，所以非英文一律走中文；国家/运营商目录的名称不在字符串资源里，
 * 由这里按语言挑 label / labelEn，避免往资源文件里塞 56 条国名。
 */
@Composable
fun englishUi(): Boolean = LocalConfiguration.current.locales[0].language == "en"

/** 运营商显示名。core 只回稳定 id，系统自己给出的名字原样透传。 */
@Composable
fun operatorName(reported: String?): String {
    @StringRes val mapped = when (reported) {
        null -> R.string.operator_unknown
        "cmcc" -> R.string.operator_cmcc
        "cucc" -> R.string.operator_cucc
        "ctcc" -> R.string.operator_ctcc
        "cbn" -> R.string.operator_cbn
        else -> return reported
    }
    return stringResource(mapped)
}
