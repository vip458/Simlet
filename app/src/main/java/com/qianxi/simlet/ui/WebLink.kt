package com.qianxi.simlet.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 交给系统浏览器打开。
 *
 * 关于页、设置页、环境自检三处都要跳外链，之前各写了一份 private 扩展；
 * runCatching 是为了没装浏览器时不崩，这一行为三处必须一致，所以收到一处。
 */
fun Context.openWebPage(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
