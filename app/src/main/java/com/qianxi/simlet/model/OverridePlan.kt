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
        carrierName?.takeIf { it.isNotBlank() }?.let {
            putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
            putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, it)
            // 副卡场景下不强制本地图标/名称，系统会忽略 carrier_name_string
            putBoolean(CarrierConfigManager.KEY_FORCE_HOME_NETWORK_BOOL, true)
        }
    }

    companion object {
        fun from(countryIso: String?, carrierName: String?): OverridePlan =
            OverridePlan(
                countryIso = countryIso?.takeIf { it.isNotBlank() }?.lowercase(),
                carrierName = carrierName?.takeIf { it.isNotBlank() }
            )
    }
}
