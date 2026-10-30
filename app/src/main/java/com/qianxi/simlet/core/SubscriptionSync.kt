package com.qianxi.simlet.core

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log

/**
 * 状态栏与系统上报的运营商名跟随。
 *
 * 只改 carrier config 时，部分机型不会把新名字写进 SubscriptionInfo（状态栏仍显示真实运营商），
 * 各厂商又各有私有入口，因此这里按优先级逐条尝试，并在每次尝试后读回校验。
 * 全部失败也不影响国家码覆盖本身生效，故只记日志不抛异常。
 */
internal object SubscriptionSync {

    private const val TAG = "SimletSync"
    private const val PHONE = "phone"
    private const val PHONE_STUB = "com.android.internal.telephony.ITelephony\$Stub"
    private const val ISUB = "isub"
    private const val ISUB_STUB = "com.android.internal.telephony.ISub\$Stub"

    /** 覆盖写入后系统刷新需要时间，按此节奏重试并读回校验。 */
    private val RETRY_DELAY_MS = longArrayOf(0L, 500L, 1500L, 3000L)

    /**
     * @return 系统上报的运营商名是否已跟随。false 只代表还需时间刷新，不代表写入失败，
     * 因此调用方只记日志，不据此判定整次保存失败。
     */
    fun sync(context: Context, subscriptionId: Int, carrierName: String?): Boolean {
        val appContext = context.applicationContext
        val expected = carrierName?.takeIf { it.isNotBlank() }
        var lastError: Throwable? = null

        RETRY_DELAY_MS.forEachIndexed { attempt, delayMs ->
            if (delayMs > 0) Thread.sleep(delayMs)
            val outcome = runCatching { push(appContext, subscriptionId, expected) }
                .onFailure { lastError = it }
            if (outcome.isSuccess && followed(subscriptionId, expected)) {
                Log.i(TAG, "运营商名已跟随 sub=$subscriptionId name=$expected 第${attempt + 1}次")
                return true
            }
        }

        Log.w(
            TAG,
            "运营商名暂未跟随 sub=$subscriptionId 期望=$expected " +
                "实际=${effectiveName(subscriptionId)} 错误=${lastError?.message}"
        )
        return false
    }

    /**
     * 还原时没有可比对的期望值（系统会退回真实运营商名），写入不报错即算完成；
     * 覆盖时校验的是 service state 里的实际名称，SubscriptionInfo.carrierName 不随品牌覆盖变化。
     */
    private fun followed(subscriptionId: Int, expected: String?): Boolean {
        if (expected == null) return true
        return effectiveName(subscriptionId) == expected || readBrand(subscriptionId) == expected
    }

    private fun effectiveName(subscriptionId: Int): String? =
        RegistryDump.effectiveName(phoneIdOf(subscriptionId))

    private fun push(context: Context, subscriptionId: Int, carrierName: String?) {
        LoaderChannel.notifyChanged(subscriptionId)

        val paths = buildList {
            add("TelephonyManager" to { setViaManager(context, subscriptionId, carrierName) })
            add("ITelephony" to { setViaPhoneService(subscriptionId, carrierName) })
            add("ISub" to { setViaSubService(subscriptionId, carrierName) })
            addAll(oplusPaths(subscriptionId, carrierName))
        }

        var lastError: Throwable? = null
        for ((name, action) in paths) {
            runCatching(action)
                .onSuccess {
                    Log.d(TAG, "运营商名走 $name 通道 sub=$subscriptionId")
                    LoaderChannel.notifyChanged(subscriptionId)
                    return
                }
                .onFailure { error ->
                    // 每条通道的拒绝原因都留痕：机型上通常只有一两条是「不支持」，
                    // 真正的原因往往是权限被拒，只看最后一条会误判。
                    lastError = error
                    Log.d(TAG, "$name 通道不可用 sub=$subscriptionId: ${error.message}")
                }
        }
        throw lastError ?: IllegalStateException("没有可用的运营商名通道")
    }

    /** Oplus（一加/OPPO/真我）私有通道，非该系机型取不到服务，返回空列表即可。 */
    private fun oplusPaths(subscriptionId: Int, carrierName: String?): List<Pair<String, () -> Unit>> {
        val internalExt = SystemServices.proxy(
            "oplus_telephony_internal_ext",
            "com.android.internal.telephony.IOplusTelephonyInternalExt\$Stub"
        ) ?: return emptyList()

        return listOf(
            "IOplusTelephonyInternalExt" to {
                invokeMatching(internalExt, "setCarrierName", subscriptionId, carrierName)
                    ?: throw NoSuchMethodException("setCarrierName 签名不匹配")
            },
            "IOplusTelephonyInternalExt.updateSpn" to {
                val phoneId = phoneIdOf(subscriptionId)
                val method = internalExt.javaClass.methods.firstOrNull { it.name == "updateSpn" }
                    ?: throw NoSuchMethodException("updateSpn 不存在")
                val args = when (method.parameterCount) {
                    1 -> arrayOf<Any?>(phoneId)
                    2 -> arrayOf<Any?>(phoneId, subscriptionId)
                    else -> throw NoSuchMethodException("updateSpn 签名不匹配")
                }
                SystemServices.invoke(method, internalExt, *args)
            }
        )
    }

    private fun setViaManager(context: Context, subscriptionId: Int, carrierName: String?) {
        val manager = context.getSystemService(TelephonyManager::class.java)
            ?.createForSubscriptionId(subscriptionId)
            ?: throw IllegalStateException("TelephonyManager 不可用")
        val method = SystemServices.method(manager, "setOperatorBrandOverride", String::class.java)
            ?: throw NoSuchMethodException("setOperatorBrandOverride 不可用")
        if (SystemServices.invoke(method, manager, carrierName) as? Boolean == false) {
            throw IllegalStateException("TelephonyManager.setOperatorBrandOverride 返回 false")
        }
    }

    private fun setViaPhoneService(subscriptionId: Int, carrierName: String?) {
        val phone = SystemServices.proxy(PHONE, PHONE_STUB)
            ?: throw IllegalStateException("ITelephony 不可用")
        val method = phone.javaClass.methods.firstOrNull {
            it.name == "setOperatorBrandOverride" && it.parameterCount == 2
        } ?: throw NoSuchMethodException("ITelephony.setOperatorBrandOverride 不存在")

        val args = if (method.parameterTypes[0] == Int::class.javaPrimitiveType) {
            arrayOf<Any?>(subscriptionId, carrierName)
        } else {
            arrayOf<Any?>(carrierName, subscriptionId)
        }
        if (SystemServices.invoke(method, phone, *args) as? Boolean == false) {
            throw IllegalStateException("ITelephony.setOperatorBrandOverride 返回 false")
        }
    }

    /** 部分 ROM 的 ISub 仍保留 setCarrierName，可直接改 SubscriptionInfo。 */
    private fun setViaSubService(subscriptionId: Int, carrierName: String?) {
        val iSub = SystemServices.proxy(ISUB, ISUB_STUB)
            ?: throw IllegalStateException("ISub 不可用")
        invokeMatching(iSub, "setCarrierName", subscriptionId, carrierName)
            ?: throw NoSuchMethodException("ISub.setCarrierName 签名不匹配")
    }

    /** 按 (int,String) / (String,int) / (int,String,String) 三种常见签名尝试调用。 */
    private fun invokeMatching(target: Any, methodName: String, subscriptionId: Int, value: String?): Any? {
        val method = target.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterCount in 2..3
        } ?: return null

        val args = when {
            method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                method.parameterTypes[1] == String::class.java ->
                if (method.parameterCount == 3) {
                    arrayOf<Any?>(subscriptionId, value, "com.android.shell")
                } else {
                    arrayOf<Any?>(subscriptionId, value)
                }

            method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType ->
                arrayOf<Any?>(value, subscriptionId)

            else -> return null
        }
        return SystemServices.invoke(method, target, *args)
    }

    private fun phoneIdOf(subscriptionId: Int): Int = runCatching {
        val iSub = SystemServices.proxy(ISUB, ISUB_STUB) ?: return 0
        val method = iSub.javaClass.getMethod("getPhoneId", Int::class.javaPrimitiveType)
        method.invoke(iSub, subscriptionId) as Int
    }.getOrDefault(0)

    private fun readBrand(subscriptionId: Int): String? = runCatching {
        val phone = SystemServices.proxy(PHONE, PHONE_STUB) ?: return null
        val method = phone.javaClass.methods.firstOrNull {
            it.name == "getOperatorBrandOverride" && it.parameterCount == 1
        } ?: return null
        method.invoke(phone, subscriptionId)?.toString()?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
