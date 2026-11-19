package sh.yetkin.esigner.app

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.Properties

sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data object UpToDate : UpdateCheck
    /** [asset]: this platform's package, downloaded in the app; without one the releases page is opened. */
    data class Available(val version: String, val asset: Asset? = null, val downloadFailed: Boolean = false) : UpdateCheck
    data class Downloading(val version: String) : UpdateCheck
    data class Downloaded(val version: String, val file: File) : UpdateCheck
    data object Failed : UpdateCheck
}

/** A release package and the SHA-256 GitHub computed when it was uploaded. */
data class Asset(val url: String, val sha256: String) {
    val name: String get() = url.substringAfterLast('/')
}

/** App version and the latest GitHub release; asked once at startup and when the user checks. */
object Updates {
    const val REPOSITORY_URL = "https://github.com/ayetkin/e-signer"
    const val RELEASES_URL = "$REPOSITORY_URL/releases/latest"
    private const val LATEST_API = "https://api.github.com/repos/ayetkin/e-signer/releases/latest"

    /** Written into the jar by the build (appVersion in gradle.properties, or -PappVersion). */
    val current: String = Updates::class.java.getResourceAsStream("/version.properties")
        ?.use { Properties().apply { load(it) }.getProperty("version") } ?: "dev"

    fun check(): UpdateCheck {
        val request = HttpRequest.newBuilder(URI(LATEST_API))
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration.ofSeconds(10))
            .build()
        val response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
            .use { it.send(request, HttpResponse.BodyHandlers.ofString()) }
        // 404: no release published yet.
        if (response.statusCode() == 404) return UpdateCheck.UpToDate
        if (response.statusCode() != 200) throw IOException("GitHub answered ${response.statusCode()}")
        val latest = Regex("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"").find(response.body())?.groupValues?.get(1)
            ?: throw IOException("No tag_name in the GitHub answer")
        if (!isNewer(latest, current)) return UpdateCheck.UpToDate
        return UpdateCheck.Available(latest, platformSuffix()?.let { asset(response.body(), it) })
    }

    /** The release package for this machine, as named by the release workflow; null on Linux (deb or tar.gz). */
    private fun platformSuffix(): String? {
        val os = System.getProperty("os.name").lowercase()
        val arm = System.getProperty("os.arch") in setOf("aarch64", "arm64")
        return when {
            os.startsWith("mac") -> if (arm) "-macos-arm64.dmg" else "-macos-x64.dmg"
            os.startsWith("windows") -> "-windows-x64.msi"
            else -> null
        }
    }

    /**
     * The asset of the release answer whose name ends in [suffix], with its digest. Both are fields of the same asset
     * object (no braces between them); an asset without a digest is not offered, since it could not be checked.
     */
    internal fun asset(json: String, suffix: String): Asset? {
        val pattern = Regex(
            "\"digest\"\\s*:\\s*\"sha256:([0-9a-f]{64})\"[^{}]*?" +
                "\"browser_download_url\"\\s*:\\s*\"(" + Regex.escape("$REPOSITORY_URL/releases/download/") +
                "[^\"/]+/[^\"/]+" + Regex.escape(suffix) + ")\""
        )
        return pattern.find(json)?.let { Asset(it.groupValues[2], it.groupValues[1]) }
    }

    /** The user's Downloads folder, or their home folder where there is none. */
    fun downloadDir(): File {
        val home = File(System.getProperty("user.home"))
        return File(home, "Downloads").takeIf { it.isDirectory } ?: home
    }

    /**
     * Downloads [asset] into [dir] and checks its SHA-256 before it gets its real name, so a broken or altered
     * download never sits there under the package name. A file already there with the same name is kept: it is
     * returned when it is this package, otherwise the download gets a free name ("... (2).dmg").
     */
    fun download(asset: Asset, dir: File): File {
        val existing = File(dir, asset.name)
        if (existing.isFile && sha256(existing.inputStream()) == asset.sha256) return existing
        val part = File(dir, asset.name + ".part")
        // HttpURLConnection has a socket read timeout; HttpClient's timeout stops at the headers, and a stalled
        // body would leave the download running forever. GitHub redirects to its file host, https to https.
        val connection = URI(asset.url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode != 200) throw IOException("GitHub answered ${connection.responseCode}")
            val sha256 = sha256(connection.inputStream) { input -> part.outputStream().use { input.copyTo(it) } }
            if (sha256 != asset.sha256) throw IOException("${asset.name}: SHA-256 mismatch")
            val target = generateSequence(1) { it + 1 }
                .map { n -> if (n == 1) existing else File(dir, "${existing.nameWithoutExtension} ($n).${existing.extension}") }
                .first { !it.exists() }
            Files.move(part.toPath(), target.toPath(), ATOMIC_MOVE)
            return target
        } finally {
            connection.disconnect()
            part.delete()
        }
    }

    /** SHA-256 (hex) of everything [read] takes from [stream]; by default it reads the stream to its end. */
    private fun sha256(
        stream: InputStream,
        read: (InputStream) -> Unit = { it.transferTo(OutputStream.nullOutputStream()) },
    ): String = DigestInputStream(stream, MessageDigest.getInstance("SHA-256")).use { input ->
        read(input)
        HexFormat.of().formatHex(input.messageDigest.digest())
    }

    /**
     * Numeric comparison of dotted versions ("0.10.0" > "0.9.3"). With equal numbers a release is newer than a
     * pre-release ("0.5.0" > "0.5.0-rc1"); two suffixes are not compared.
     */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return '-' !in latest && '-' in current
    }
}
