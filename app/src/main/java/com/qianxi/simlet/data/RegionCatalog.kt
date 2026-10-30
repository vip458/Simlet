package com.qianxi.simlet.data

import java.util.Locale

/**
 * 国家/地区目录：Simlet 的写入目标与运营商分组的唯一数据源。
 *
 * @param iso ISO 3166-1 alpha-2，直接写入 sim_country_iso_override_string（小写）
 * @param label 简体中文名称
 * @param common 是否出现在首页快捷选择中
 */
data class Region(
    val iso: String,
    val label: String,
    val common: Boolean = false
) {
    /**
     * 英文名交给 ICU 生成，避免手抄 56 条国名抄出错别字。
     *
     * 中文词条对三个地区带了「中国」前缀，英文必须保持同一口径，所以显式覆盖 ICU 的裸名称。
     */
    val labelEn: String get() = EnglishLabels[iso] ?: Locale("", iso.uppercase()).getDisplayCountry(Locale.ENGLISH)

    /** 中英双语都参与匹配：英文界面下搜「中国」、中文界面下搜 "Japan" 都应该出结果。 */
    fun matches(query: String): Boolean {
        val keyword = query.trim()
        if (keyword.isEmpty()) return true
        return label.contains(keyword) ||
            labelEn.contains(keyword, ignoreCase = true) ||
            iso.contains(keyword.lowercase())
    }

    fun displayName(english: Boolean): String = if (english) labelEn else label
}

private val EnglishLabels = mapOf(
    "hk" to "Hong Kong, China",
    "mo" to "Macao, China",
    "tw" to "Taiwan, China"
)

object RegionCatalog {

    val all: List<Region> = listOf(
        Region("cn", "中国", common = true),
        Region("hk", "中国香港", common = true),
        Region("mo", "中国澳门"),
        Region("tw", "中国台湾", common = true),
        Region("jp", "日本", common = true),
        Region("kr", "韩国", common = true),
        Region("us", "美国", common = true),
        Region("ca", "加拿大"),
        Region("gb", "英国", common = true),
        Region("de", "德国"),
        Region("fr", "法国"),
        Region("it", "意大利"),
        Region("es", "西班牙"),
        Region("pt", "葡萄牙"),
        Region("nl", "荷兰"),
        Region("se", "瑞典"),
        Region("no", "挪威"),
        Region("dk", "丹麦"),
        Region("fi", "芬兰"),
        Region("pl", "波兰"),
        Region("cz", "捷克"),
        Region("at", "奥地利"),
        Region("ch", "瑞士"),
        Region("ie", "爱尔兰"),
        Region("ru", "俄罗斯"),
        Region("ua", "乌克兰"),
        Region("tr", "土耳其"),
        Region("il", "以色列"),
        Region("sa", "沙特阿拉伯"),
        Region("ae", "阿联酋"),
        Region("qa", "卡塔尔"),
        Region("kw", "科威特"),
        Region("in", "印度", common = true),
        Region("pk", "巴基斯坦"),
        Region("bd", "孟加拉国"),
        Region("id", "印度尼西亚"),
        Region("my", "马来西亚"),
        Region("th", "泰国", common = true),
        Region("vn", "越南"),
        Region("ph", "菲律宾"),
        Region("sg", "新加坡", common = true),
        Region("au", "澳大利亚"),
        Region("nz", "新西兰"),
        Region("fj", "斐济"),
        Region("br", "巴西"),
        Region("ar", "阿根廷"),
        Region("mx", "墨西哥"),
        Region("cl", "智利"),
        Region("co", "哥伦比亚"),
        Region("pe", "秘鲁"),
        Region("za", "南非"),
        Region("ng", "尼日利亚"),
        Region("ke", "肯尼亚"),
        Region("eg", "埃及"),
        Region("ma", "摩洛哥")
    )

    private val byIso: Map<String, Region> = all.associateBy { it.iso }

    fun find(iso: String?): Region? = iso?.lowercase()?.let(byIso::get)

    fun labelOf(iso: String?, english: Boolean = false): String? = find(iso)?.displayName(english)

    /** 规范化用户输入：仅接受两位字母，返回小写 ISO；否则 null。 */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim().lowercase()
        return trimmed.takeIf { it.length == 2 && it.all(Char::isLetter) }
    }
}
