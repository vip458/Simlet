package com.qianxi.simlet.model

import android.os.PersistableBundle
import android.telephony.CarrierConfigManager

/**
 * 一次写入的目标内容。两个字段都允许为空，空表示该项不改。
 *
 * @param countryIso 小写两位国家码，对应 sim_country_iso_override_string
 * @param carrierName 运营商名称，对应 carrier_name_string（同时置 carrier_name_override_bool）
 */
data class OverridePlan(
    val countryIso: String? = null,
    val carrierName: String? = null
) {
    val empty: Boolean get() = countryIso.isNullOrBlank() && carrierName.isNullOrBlank()

    fun toBundle(): PersistableBundle = PersistableBundle().apply {
        countryIso?.takeIf { it.isNotBlank() }?.let {
            putString(CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING, it.lowercase())
        }
        cleanCarrierName(carrierName)?.let {
            putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
            putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, it)
            // 副卡场景下不强制本地图标/名称，系统会忽略 carrier_name_string
            putBoolean(CarrierConfigManager.KEY_FORCE_HOME_NETWORK_BOOL, true)
        }
    }

    companion object {
        /**
         * 运营商名写进系统后出现在状态栏、拨号盘和「设置-SIM 卡」里，那些位置都不换行。
         * 64 足够写「中国联通 5G」这种长度，再长只会把界面顶开；上限设在写库之前，
         * 而不是让界面自己去截。
         */
        const val MAX_CARRIER_NAME = 64

        fun from(countryIso: String?, carrierName: String?): OverridePlan =
            OverridePlan(
                countryIso = countryIso?.takeIf { it.isNotBlank() }?.lowercase(),
                carrierName = cleanCarrierName(carrierName)
            )

        /**
         * 去掉不可见字符并按上限截断；整条都不剩时返回 null，意思是「这一项不改」。
         *
         * 控制字符和方向标记（`\u202E` 那一类）会原样存进系统运营商配置，状态栏按它重排文本——
         * 一个看起来是「MOCMOC」其实显示成别的样子的名字就出来了。这一格没有任何格式约束，
         * 所以清一遍放在模型层：粘贴板、prefs 里的旧值、开机补写读回来的系统值都走这同一条。
         */
        fun cleanCarrierName(raw: String?): String? = raw
            ?.replace(INVISIBLE, "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(MAX_CARRIER_NAME)

        private val INVISIBLE = Regex("[\\u0000-\\u001F\\u007F-\\u009F\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]")
    }
}
