package com.qianxi.simlet.core

import android.util.Log
import com.qianxi.simlet.BuildConfig

/**
 * 带用户数据的日志出口：只在调试包出声。
 *
 * release 包往 logcat 写「slot=1 sub=45 plan=OverridePlan(运营商名)」，等于把这个人挑了什么
 * 留了一份本机随时可捞的副本：插上 adb 就能读，部分厂商 ROM 上还留着跨应用读日志的口子。
 * 这些信息调试期确实要，所以按构建档位关掉而不是删掉——删了下次真机出问题只能重装调试包。
 *
 * 界面已经能看到的失败原因（toast、诊断串）不走这里，那些不是日志。
 */
internal object AppLog {

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(tag, message, error)
    }
}
