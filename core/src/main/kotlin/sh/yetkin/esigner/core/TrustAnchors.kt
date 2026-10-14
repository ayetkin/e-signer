package sh.yetkin.esigner.core

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Properties

/**
 * Root certificates a signature chain must reach to be trusted. The bundled set lives in
 * `trust/anchors.properties` (file -> SHA-256); a file whose hash does not match is rejected.
 */
class TrustAnchors(val certificates: List<X509Certificate>) {
    companion object {
        private const val DIR = "/trust/"

        fun bundled(): TrustAnchors {
            val manifest = Properties().apply {
                TrustAnchors::class.java.getResourceAsStream(DIR + "anchors.properties").use { load(it) }
            }
            val factory = CertificateFactory.getInstance("X.509")
            val certificates = manifest.stringPropertyNames().sorted().map { name ->
                val bytes = TrustAnchors::class.java.getResourceAsStream(DIR + name)!!.use { it.readBytes() }
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                check(digest == manifest.getProperty(name).trim().lowercase()) { "Trust anchor hash mismatch: $name" }
                factory.generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
            }
            return TrustAnchors(certificates)
        }
    }
}
