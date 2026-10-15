package sh.yetkin.esigner.app

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Properties

sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Available(val version: String) : UpdateCheck
    data object Failed : UpdateCheck
}

/** App version and the latest GitHub release; only asked when the user checks, never in the background. */
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
            .send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) throw IOException("GitHub answered ${response.statusCode()}")
        val latest = Regex("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"").find(response.body())?.groupValues?.get(1)
            ?: throw IOException("No tag_name in the GitHub answer")
        return if (isNewer(latest, current)) UpdateCheck.Available(latest) else UpdateCheck.UpToDate
    }

    /** Numeric comparison of dotted versions ("0.10.0" > "0.9.3"); a suffix such as "-rc1" is ignored. */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }
}
