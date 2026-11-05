package com.qianxi.simlet.update

import com.qianxi.simlet.remote.HubRules
import com.qianxi.simlet.remote.parseRelease
import com.qianxi.simlet.remote.toJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/**
 * 后台登记的包摘要这一环：它是「下载到的东西就是我认为的那个文件」唯一的实证。
 *
 * URL 白名单和限速都拦不住「域名合规、内容被换过」，而校验写错时用户只看到「装不上」，
 * 分不清是自己网差还是包被动过。所以这里既钉住「对得上要放行」，也钉住
 * 「登记得不对就拒绝」——后者不许退化成「当成没登记，不校验」。
 */
class ApkDigestTest {

    @Test
    fun `算得对的摘要放行`() {
        val payload = ByteArray(4096) { (it % 251).toByte() }
        assertTrue(ApkInstaller.digestMatches(writeTemp(payload), sha256Hex(payload)))
    }

    @Test
    fun `内容被换过一个字节就不放行`() {
        val payload = ByteArray(4096) { (it % 251).toByte() }
        val file = writeTemp(payload)
        val expected = sha256Hex(payload)
        assertTrue(ApkInstaller.digestMatches(file, expected))
        // 落地之后再动一个字节：这就是「下载看着没问题、要装的已经不是那个包」的形态。
        file.writeBytes(ByteArray(4096) { if (it == 2048) 7 else (it % 251).toByte() })
        assertFalse(ApkInstaller.digestMatches(file, expected))
    }

    @Test
    fun `大小写和空白可以救，形状不对的一律当不匹配`() {
        val payload = "app-release".toByteArray()
        val file = writeTemp(payload)
        val real = sha256Hex(payload)
        assertTrue(ApkInstaller.digestMatches(file, " " + real.uppercase() + " "))
        assertFalse("md5 不是 sha256", ApkInstaller.digestMatches(file, "e10adc3949ba59abbe56e057f20f883e"))
        assertFalse(ApkInstaller.digestMatches(file, real.substring(0, 63)))
        assertFalse(ApkInstaller.digestMatches(file, real + "0"))
        assertFalse(ApkInstaller.digestMatches(file, "zz" + real.substring(2)))
        // 空串在这里返回 false 不代表「校验失败」：调用方只在登记值非空时才走到这一步。
        assertFalse(ApkInstaller.digestMatches(file, ""))
    }

    @Test
    fun `归一化只去空白转小写`() {
        val hex = "AB12".repeat(16)
        assertEquals(hex.lowercase(), HubRules.digest("  $hex\n"))
        assertEquals("", HubRules.digest("   "))
    }

    @Test
    fun `下发里的 dg 解析进模型，缓存回来还是同一个值`() {
        val hex = sha256Hex("cache round trip".toByteArray())
        val raw = JSONObject(
            """
            {"nm":"1.0.3","cd":4,"pg":"https://github.com/vip458/Simlet/releases/tag/v1.0.3",
             "ap":"https://github.com/vip458/Simlet/releases/download/v1.0.3/app-release.apk",
             "sz":1438720,"ro":100,"bk":"v4","dg":"${hex.uppercase()}"}
            """.trimIndent()
        )
        val release = parseRelease(raw)
        assertEquals(hex, release.sha256)
        assertEquals(hex, parseRelease(release.toJson()).sha256)
    }

    @Test
    fun `旧版本没登记 dg 时是空串，不影响其它判定`() {
        val release = parseRelease(JSONObject("""{"nm":"1.0.2","cd":3,"ro":100,"bk":"v3"}"""))
        assertEquals("", release.sha256)
        assertEquals(3, release.code)
    }

    private fun writeTemp(content: ByteArray): File =
        Files.createTempFile("simlet-digest", ".apk").toFile().apply {
            deleteOnExit()
            writeBytes(content)
        }

    private fun sha256Hex(content: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(content)
            .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
