package sh.yetkin.esigner.core

import eu.europa.esig.dss.token.Pkcs12SignatureToken
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

/** Self-signed software key standing in for the card. The national id is a fake value. */
object TestKeys {
    const val FAKE_NATIONAL_ID = "12345678901"
    private val password = "test".toCharArray()

    val certificate: X509Certificate
    private val keyStoreBytes: ByteArray

    init {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject = X500NameBuilder(BCStyle.INSTANCE)
            .addRDN(BCStyle.C, "TR")
            .addRDN(BCStyle.SERIALNUMBER, FAKE_NATIONAL_ID)
            .addRDN(BCStyle.CN, "Test User")
            .build()
        val now = Instant.now()
        val holder = JcaX509v3CertificateBuilder(
            subject, BigInteger.ONE,
            Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(365, ChronoUnit.DAYS)),
            subject, keyPair.public,
        )
            .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.nonRepudiation))
            .build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
        certificate = JcaX509CertificateConverter().getCertificate(holder)

        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("signer", keyPair.private, password, arrayOf(certificate))
        }
        keyStoreBytes = ByteArrayOutputStream().also { keyStore.store(it, password) }.toByteArray()
    }

    fun token() = Pkcs12SignatureToken(keyStoreBytes, KeyStore.PasswordProtection(password))
}
