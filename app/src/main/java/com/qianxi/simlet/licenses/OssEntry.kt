package com.qianxi.simlet.licenses

/**
 * 一条第三方开源条目。
 *
 * [version] 对 maven 依赖是实际解析到的版本号；图标集不在 maven 里，填的是钉住的提交短 sha。
 * [url] 是整行点过去的项目地址。
 */
data class OssEntry(
    val name: String,
    val author: String,
    val license: String,
    val version: String,
    val url: String
)

/**
 * 许可页那一整张列表。
 *
 * maven 部分由 `tools/gen-licenses.py` 从解析出的依赖与它们的 POM 生成；
 * 图标集不在 maven 里，手写在这里。两者混排成一份、按名字排序，
 * 不再单列「第三方图标」那种分类——对用户来说它们是一回事。
 */
val openSourceEntries: List<OssEntry> = (
    mavenLicenses + listOf(
        OssEntry(
            name = "Reicon",
            author = "dqev",
            license = "MIT",
            version = "fe69753",
            url = "https://reicon.dev"
        )
    )
    ).sortedBy { it.name.lowercase() }
