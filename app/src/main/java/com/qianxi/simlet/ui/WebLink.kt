package com.qianxi.simlet.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.qianxi.simlet.remote.HubRules

/**
 * 交给系统浏览器打开。
 *
 * 关于页、设置页、环境自检三处都要跳外链，之前各写了一份 private 扩展；
 * runCatching 是为了没装浏览器时不崩，这一行为三处必须一致，所以收到一处。
 *
 * 地址先过 [HubRules.webUrl]：这条链路上的 URL 有一半来自后台下发的字段，
 * 而 `intent://`、`file://`、`tel:`、`sms:` 交给 ACTION_VIEW 不是「打开网页」，
 * 是拉起别的应用组件、甚至拨号。只放行 http/https，其余静默不响应。
 */
fun Context.openWebPage(url: String) {
    val target = HubRules.webUrl(url) ?: return
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
