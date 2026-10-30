package com.qianxi.simlet.data

/**
 * 运营商预设，按国家/地区维度分组（写入 carrier_name_string 的取值）。
 *
 * @param value 实际写入系统的运营商名称
 * @param label 界面展示名称
 * @param region 归属国家 ISO，用于分组与联动过滤
 */
data class TelecomOperator(
    val value: String,
    val label: String,
    val region: String
) {
    /** 写入值本身就是国际通用名，英文界面直接用它，不必再造一份词条。 */
    fun displayName(english: Boolean): String = if (english) value else label
}

data class OperatorGroup(
    val region: Region,
    val operators: List<TelecomOperator>
)

object OperatorCatalog {

    private val raw: List<TelecomOperator> = listOf(
        // 中国
        TelecomOperator("China Mobile", "中国移动", "cn"),
        TelecomOperator("China Unicom", "中国联通", "cn"),
        TelecomOperator("China Telecom", "中国电信", "cn"),
        TelecomOperator("China Broadnet", "中国广电", "cn"),
        // 中国香港
        TelecomOperator("CMHK", "中国移动香港", "hk"),
        TelecomOperator("3HK", "3HK", "hk"),
        TelecomOperator("HKT", "HKT 香港電訊", "hk"),
        TelecomOperator("SmarTone", "SmarTone", "hk"),
        // 中国台湾
        TelecomOperator("Chunghwa Telecom", "中華電信", "tw"),
        TelecomOperator("Taiwan Mobile", "台灣大哥大", "tw"),
        TelecomOperator("FarEasTone", "遠傳電信", "tw"),
        // 日本
        TelecomOperator("docomo", "NTT docomo", "jp"),
        TelecomOperator("IIJmio", "IIJmio", "jp"),
        TelecomOperator("au", "KDDI au", "jp"),
        TelecomOperator("Rakuten", "乐天移动", "jp"),
        TelecomOperator("SoftBank", "SoftBank", "jp"),
        // 韩国
        TelecomOperator("SK Telecom", "SK Telecom", "kr"),
        TelecomOperator("KT", "KT", "kr"),
        TelecomOperator("LG U+", "LG U+", "kr"),
        // 美国
        TelecomOperator("T-Mobile", "T-Mobile", "us"),
        TelecomOperator("AT&T", "AT&T", "us"),
        TelecomOperator("Verizon", "Verizon", "us"),
        TelecomOperator("US Cellular", "US Cellular", "us"),
        TelecomOperator("Mint Mobile", "Mint Mobile", "us"),
        // 加拿大
        TelecomOperator("Rogers", "Rogers", "ca"),
        TelecomOperator("Bell", "Bell Canada", "ca"),
        TelecomOperator("Telus", "Telus", "ca"),
        // 英国
        TelecomOperator("EE", "EE", "gb"),
        TelecomOperator("O2", "O2 UK", "gb"),
        TelecomOperator("Vodafone UK", "Vodafone UK", "gb"),
        TelecomOperator("Three UK", "Three UK", "gb"),
        // 欧洲主要
        TelecomOperator("Vodafone DE", "Vodafone 德国", "de"),
        TelecomOperator("DT DE", "德国电信", "de"),
        TelecomOperator("O2 DE", "O2 德国", "de"),
        TelecomOperator("Orange FR", "Orange 法国", "fr"),
        TelecomOperator("SFR", "SFR", "fr"),
        TelecomOperator("Bouygues", "Bouygues Telecom", "fr"),
        TelecomOperator("TIM IT", "TIM 意大利", "it"),
        TelecomOperator("Vodafone IT", "Vodafone 意大利", "it"),
        TelecomOperator("Movistar", "Movistar 西班牙", "es"),
        TelecomOperator("Vodafone ES", "Vodafone 西班牙", "es"),
        // 俄罗斯 / 土耳其
        TelecomOperator("MTS", "MTS 俄罗斯", "ru"),
        TelecomOperator("MegaFon", "MegaFon", "ru"),
        TelecomOperator("Turkcell", "Turkcell", "tr"),
        // 中东
        TelecomOperator("STC", "STC 沙特", "sa"),
        TelecomOperator("Etisalat", "Etisalat 阿联酋", "ae"),
        TelecomOperator("du", "du 阿联酋", "ae"),
        TelecomOperator("Ooredoo", "Ooredoo 卡塔尔", "qa"),
        // 南亚
        TelecomOperator("Jio", "Reliance Jio", "in"),
        TelecomOperator("Airtel", "Bharti Airtel", "in"),
        TelecomOperator("Vi", "Vodafone Idea", "in"),
        TelecomOperator("Zong", "Zong 巴基斯坦", "pk"),
        // 东南亚
        TelecomOperator("Telkomsel", "Telkomsel", "id"),
        TelecomOperator("XL", "XL Axiata", "id"),
        TelecomOperator("Maxis", "Maxis", "my"),
        TelecomOperator("Celcom", "Celcom", "my"),
        TelecomOperator("AIS", "AIS 泰国", "th"),
        TelecomOperator("TrueMove", "TrueMove H", "th"),
        TelecomOperator("dtac", "dtac", "th"),
        TelecomOperator("Viettel", "Viettel", "vn"),
        TelecomOperator("Globe", "Globe 菲律宾", "ph"),
        TelecomOperator("Smart", "Smart 菲律宾", "ph"),
        TelecomOperator("Singtel", "Singtel", "sg"),
        TelecomOperator("StarHub", "StarHub", "sg"),
        TelecomOperator("M1", "M1 新加坡", "sg"),
        // 大洋洲 / 美洲 / 非洲
        TelecomOperator("Telstra", "Telstra", "au"),
        TelecomOperator("Optus", "Optus", "au"),
        TelecomOperator("Spark", "Spark 新西兰", "nz"),
        TelecomOperator("Vivo", "Vivo 巴西", "br"),
        TelecomOperator("Claro", "Claro", "mx"),
        TelecomOperator("Telcel", "Telcel", "mx"),
        TelecomOperator("Vodafone SA", "Vodafone 南非", "za")
    )

    private val regionIndex: Map<String, Region> =
        RegionCatalog.all.associateBy { it.iso }

    /** 保持目录内国家顺序的分组结果。 */
    val grouped: List<OperatorGroup> = raw.groupBy { it.region }
        .mapNotNull { (iso, operators) ->
            val region = regionIndex[iso] ?: return@mapNotNull null
            OperatorGroup(region = region, operators = operators.sortedBy { it.label })
        }
        .sortedBy { entry -> RegionCatalog.all.indexOf(entry.region) }

    val allOperators: List<TelecomOperator> = raw

    fun findByValue(value: String?): TelecomOperator? =
        raw.firstOrNull { it.value == value }

    /** 关键字搜索：写入值、中英文展示名与归属国家名都参与匹配。 */
    fun search(query: String): List<OperatorGroup> {
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) return grouped
        return grouped.mapNotNull { group ->
            val regionHit = group.region.matches(keyword)
            val matched = group.operators.filter {
                regionHit || it.value.lowercase().contains(keyword) || it.label.lowercase().contains(keyword)
            }
            if (regionHit) group else if (matched.isNotEmpty()) group.copy(operators = matched) else null
        }
    }
}
