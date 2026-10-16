package sh.yetkin.esigner.core

import eu.europa.esig.dss.model.x509.CertificateToken
import eu.europa.esig.dss.service.crl.OnlineCRLSource
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader
import eu.europa.esig.dss.service.http.commons.OCSPDataLoader
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource
import eu.europa.esig.dss.spi.x509.aia.DefaultAIASource
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException

private const val TIMEOUT_MS = 10_000
private const val CACHE_NANOS = 2 * 60 * 1_000_000_000L

/**
 * CRL and CA certificate (AIA) downloads kept for two minutes, so a batch of files fetches each URL once
 * instead of once per file; parallel misses wait for the one download. OCSP goes through its own loader and
 * is always asked fresh. Only DER answers are kept, never an error page.
 */
internal class CachingDataLoader : CommonsDataLoader() {
    private class Entry(val at: Long, val data: CompletableFuture<ByteArray?> = CompletableFuture())

    private val cache = ConcurrentHashMap<String, Entry>()

    init {
        timeoutConnection = TIMEOUT_MS
        timeoutResponse = TIMEOUT_MS
    }

    override fun get(url: String): ByteArray? {
        val now = System.nanoTime()
        cache.values.removeIf { now - it.at >= CACHE_NANOS }
        val mine = Entry(now)
        val entry = cache.putIfAbsent(url, mine)
        if (entry != null) {
            return try {
                entry.data.get()
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        }
        return try {
            super.get(url).also {
                // A DER CRL or certificate starts with a SEQUENCE tag.
                if (it == null || it.isEmpty() || it[0] != DER_SEQUENCE) cache.remove(url, mine)
                mine.data.complete(it)
            }
        } catch (e: Throwable) {
            cache.remove(url, mine)
            mine.data.completeExceptionally(e)
            throw e
        }
    }
}

private const val DER_SEQUENCE: Byte = 0x30

/**
 * Certificate verifier trusting [anchors]. When [online], missing intermediates (AIA) and revocation
 * data (OCSP, CRL) are fetched over the network; validation and B-LT signing both need that.
 */
internal fun certificateVerifier(
    anchors: TrustAnchors,
    online: Boolean,
    /** Shared across verifiers to reuse downloads between files; a new one when null. */
    loader: CachingDataLoader? = null,
) = CommonCertificateVerifier().apply {
    setTrustedCertSources(
        CommonTrustedCertificateSource().apply { anchors.certificates.forEach { addCertificate(CertificateToken(it)) } },
    )
    if (online) {
        val loader = loader ?: CachingDataLoader()
        val ocspLoader = OCSPDataLoader().apply {
            timeoutConnection = TIMEOUT_MS
            timeoutResponse = TIMEOUT_MS
        }
        aiaSource = DefaultAIASource(loader)
        ocspSource = OnlineOCSPSource(ocspLoader)
        crlSource = OnlineCRLSource(loader)
    } else {
        aiaSource = null
        ocspSource = null
        crlSource = null
    }
}
