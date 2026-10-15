package sh.yetkin.esigner.core

import eu.europa.esig.dss.token.Pkcs12SignatureToken
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.CertificatePolicies
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.PolicyInformation
import org.bouncycastle.asn1.x509.qualified.QCStatement
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

/** Self-signed software keys standing in for the card. The national id is a fake value. */
object TestKeys {
    const val FAKE_NATIONAL_ID = "12345678901"
    private val password = "test".toCharArray()

    const val FAKE_TAX_ID = "1234567890"

    private val plain = create(qualified = false)
    private val qualified = create(qualified = true)
    private val seal = create(qualified = false, fiscalSeal = true)

    val certificate: X509Certificate get() = plain.first

    /** Carries the Law 5070 QC statement, like a real qualified certificate. */
    val qualifiedCertificate: X509Certificate get() = qualified.first

    fun token() = Pkcs12SignatureToken(plain.second, KeyStore.PasswordProtection(password))

    fun qualifiedToken() = Pkcs12SignatureToken(qualified.second, KeyStore.PasswordProtection(password))

    /** An organization's fiscal seal (mali muhur): tax id as serial number, Kamu SM fiscal seal policy. */
    val sealCertificate: X509Certificate get() = seal.first

    fun sealToken() = Pkcs12SignatureToken(seal.second, KeyStore.PasswordProtection(password))

    private fun create(qualified: Boolean, fiscalSeal: Boolean = false): Pair<X509Certificate, ByteArray> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject = X500NameBuilder(BCStyle.INSTANCE)
            .addRDN(BCStyle.C, "TR")
            .addRDN(BCStyle.SERIALNUMBER, if (fiscalSeal) FAKE_TAX_ID else FAKE_NATIONAL_ID)
            .addRDN(BCStyle.CN, if (fiscalSeal) "Sample Company Inc" else if (qualified) "Qualified User" else "Test User")
            .build()
        val now = Instant.now()
        val builder = JcaX509v3CertificateBuilder(
            subject, BigInteger.valueOf(if (fiscalSeal) 3L else if (qualified) 2L else 1L),
            Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(365, ChronoUnit.DAYS)),
            subject, keyPair.public,
        // Kamu SM fiscal seals carry no nonRepudiation.
        ).addExtension(
            Extension.keyUsage, true,
            KeyUsage(if (fiscalSeal) KeyUsage.digitalSignature else KeyUsage.nonRepudiation or KeyUsage.digitalSignature),
        )
        if (fiscalSeal) {
            builder.addExtension(
                Extension.certificatePolicies, false,
                CertificatePolicies(PolicyInformation(ASN1ObjectIdentifier(CertificateInfo.FISCAL_SEAL_POLICY))),
            )
        }
        if (qualified) {
            builder.addExtension(
                Extension.qCStatements, false,
                DERSequence(
                    arrayOf(
                        QCStatement(ASN1ObjectIdentifier("0.4.0.1862.1.1")),
                        QCStatement(ASN1ObjectIdentifier(CertificateInfo.QC_5070)),
                    ),
                ),
            )
        }
        val certificate = JcaX509CertificateConverter()
            .getCertificate(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)))
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("signer", keyPair.private, password, arrayOf(certificate))
        }
        return certificate to ByteArrayOutputStream().also { keyStore.store(it, password) }.toByteArray()
    }
}
