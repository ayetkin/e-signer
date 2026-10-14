package sh.yetkin.esigner.core

import eu.europa.esig.dss.model.x509.CertificateToken
import eu.europa.esig.dss.service.crl.OnlineCRLSource
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader
import eu.europa.esig.dss.service.http.commons.OCSPDataLoader
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource
import eu.europa.esig.dss.spi.x509.aia.DefaultAIASource

private const val TIMEOUT_MS = 10_000

/**
 * Certificate verifier trusting [anchors]. When [online], missing intermediates (AIA) and revocation
 * data (OCSP, CRL) are fetched over the network; validation and B-LT signing both need that.
 */
internal fun certificateVerifier(anchors: TrustAnchors, online: Boolean) = CommonCertificateVerifier().apply {
    setTrustedCertSources(
        CommonTrustedCertificateSource().apply { anchors.certificates.forEach { addCertificate(CertificateToken(it)) } },
    )
    if (online) {
        val loader = CommonsDataLoader().apply {
            timeoutConnection = TIMEOUT_MS
            timeoutResponse = TIMEOUT_MS
        }
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
