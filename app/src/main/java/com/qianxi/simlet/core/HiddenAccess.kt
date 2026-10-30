package com.qianxi.simlet.core

import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Simlet 全程依赖若干非公开接口，进程内首次触碰前先放开限制。
 * 只放开前缀，不做全量豁免。
 */
internal object HiddenAccess {
    @Volatile
    private var released = false

    fun ensure() {
        if (released) return
        synchronized(this) {
            if (released) return
            runCatching {
                HiddenApiBypass.addHiddenApiExemptions("L")
                HiddenApiBypass.addHiddenApiExemptions("I")
            }
            released = true
        }
    }
}
