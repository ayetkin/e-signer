package sh.yetkin.esigner.app

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdatesTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(Updates.isNewer("0.10.0", "0.9.3"))
        assertTrue(Updates.isNewer("0.4.2", "0.4.1"))
        assertTrue(Updates.isNewer("1.0", "0.4.1"))
        assertFalse(Updates.isNewer("0.4.1", "0.4.1"))
        assertFalse(Updates.isNewer("0.4.0", "0.4.1"))
        assertFalse(Updates.isNewer("0.4.1-rc1", "0.4.1"))
        assertTrue(Updates.isNewer("0.5.0", "0.5.0-rc1"))
        assertFalse(Updates.isNewer("0.5.0-rc2", "0.5.0-rc1"))
    }

    // Field order as GitHub's releases API returns it; the uploader object sits before the digest.
    private fun asset(name: String, digest: String?) = """
        {"url":"https://api.github.com/x","id":1,"name":"$name","label":"",
         "uploader":{"login":"someone","id":2,"type":"User"},
         "content_type":"application/octet-stream","state":"uploaded","size":10,
         ${if (digest == null) "" else "\"digest\":\"sha256:$digest\","}
         "download_count":0,"created_at":"2026-11-19T10:00:00Z","updated_at":"2026-11-19T10:00:00Z",
         "browser_download_url":"https://github.com/ayetkin/e-signer/releases/download/v0.7.0/$name"}
    """

    @Test
    fun findsThePlatformAssetWithItsDigest() {
        val a = "a".repeat(64)
        val b = "b".repeat(64)
        val json = """{"tag_name":"v0.7.0","assets":[
            ${asset("E-Signer-0.7.0-macos-arm64.dmg", a)},
            ${asset("E-Signer-0.7.0-macos-x64.dmg", b)},
            ${asset("E-Signer-0.7.0-windows-x64.msi", null)}]}"""
        val found = Updates.asset(json, "-macos-x64.dmg")
        assertEquals(b, found?.sha256)
        assertEquals("E-Signer-0.7.0-macos-x64.dmg", found?.name)
        assertEquals(a, Updates.asset(json, "-macos-arm64.dmg")?.sha256)
        // No digest, nothing to check the download against.
        assertNull(Updates.asset(json, "-windows-x64.msi"))
        assertNull(Updates.asset(json.replace("https://github.com/ayetkin/", "https://example.com/ayetkin/"), "-macos-x64.dmg"))
        // Packages without the version in their name (0.7.1 and later) are found by the same suffix.
        val unversioned = Updates.asset(json.replace("E-Signer-0.7.0-", "E-Signer-"), "-macos-arm64.dmg")
        assertEquals("E-Signer-macos-arm64.dmg", unversioned?.name)
    }

    @Test
    fun downloadKeepsOnlyAMatchingFile() {
        val body = "package bytes".toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = AtomicInteger()
        server.createContext("/") {
            requests.incrementAndGet()
            it.sendResponseHeaders(200, body.size.toLong())
            it.responseBody.use { out -> out.write(body) }
        }
        server.start()
        val dir = Files.createTempDirectory("updates").toFile()
        try {
            val url = "http://127.0.0.1:${server.address.port}/v0.7.0/E-Signer-0.7.0-macos-arm64.dmg"
            val sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body))

            assertFailsWith<IOException> { Updates.download(Asset(url, "0".repeat(64)), dir) }
            assertEquals(0, dir.listFiles()!!.size)

            // Another file under the package name is kept; the download takes the next free name.
            File(dir, "E-Signer-0.7.0-macos-arm64.dmg").writeText("someone else's file")
            val file = Updates.download(Asset(url, sha256), dir)
            assertEquals("E-Signer-0.7.0-macos-arm64 (2).dmg", file.name)
            assertContentEquals(body, file.readBytes())
            assertEquals("someone else's file", File(dir, "E-Signer-0.7.0-macos-arm64.dmg").readText())

            // The same package already there is returned without another request.
            val package1 = File(dir, "E-Signer-0.7.0-macos-arm64.dmg").apply { writeBytes(body) }
            val before = requests.get()
            assertEquals(package1, Updates.download(Asset(url, sha256), dir))
            assertEquals(before, requests.get())
        } finally {
            server.stop(0)
            dir.deleteRecursively()
        }
    }
}
