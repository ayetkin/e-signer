package sh.yetkin.esigner.core

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import eu.europa.esig.dss.token.Pkcs12SignatureToken
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.DistributionPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.qualified.QCStatement
import org.bouncycastle.cert.X509v2CRLBuilder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.tsp.TSPAlgorithms
import org.bouncycastle.tsp.TimeStampRequest
import org.bouncycastle.tsp.TimeStampResponseGenerator
import org.bouncycastle.tsp.TimeStampTokenGenerator
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.tsp.TSPException
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A small CA with a qualified signer and a TSA, served over HTTP on the loopback interface: an RFC 3161
 * endpoint at `/tsa` (BouncyCastle TimeStampResponseGenerator) and the CA's CRL at `/ca.crl`. Nothing
 * leaves the machine. When [kamuSmCustomer] is set, `/tsa` requires a valid Kamu SM identity header.
 */
class LocalPki(
    private val kamuSmCustomer: Pair<Long, String>? = null,
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val base = "http://127.0.0.1:${server.address.port}"
    private val serials = AtomicLong(100)
    private val password = "test".toCharArray()

    val tsaUrl = "$base/tsa"
    val tsaRequests = AtomicInteger()
    val crlRequests = AtomicInteger()

    private val caKeys = rsa()
    val caCertificate: X509Certificate = certificate(
        CA_NAME, CA_NAME, caKeys, caKeys.private, ca = true,
    ) { addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign)) }

    private val signerKeys = rsa()
    val signerCertificate: X509Certificate = certificate(
        X500Name("C=TR,SERIALNUMBER=${TestKeys.FAKE_NATIONAL_ID},CN=Local Signer"), CA_NAME, signerKeys, caKeys.private,
    ) {
        addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.nonRepudiation or KeyUsage.digitalSignature))
        addExtension(
            Extension.qCStatements, false,
            DERSequence(arrayOf(QCStatement(ASN1ObjectIdentifier(CertificateInfo.QC_5070)))),
        )
    }

    private val tsaKeys = rsa()
    val tsaCertificate: X509Certificate = certificate(X500Name("C=TR,CN=Local TSA"), CA_NAME, tsaKeys, caKeys.private) {
        addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        addExtension(Extension.extendedKeyUsage, true, ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping))
    }

    private val crl: ByteArray = X509v2CRLBuilder(CA_NAME, Date())
        .setNextUpdate(Date.from(Instant.now().plus(7, ChronoUnit.DAYS)))
        .build(JcaContentSignerBuilder("SHA256withRSA").build(caKeys.private))
        .encoded

    val anchors = TrustAnchors(listOf(caCertificate))

    init {
        server.createContext("/ca.crl") { exchange ->
            crlRequests.incrementAndGet()
            exchange.reply(200, "application/pkix-crl", crl)
        }
        server.createContext("/tsa") { exchange -> timestamp(exchange) }
        server.start()
    }

    /** The signer key with its chain, like a card would offer it. */
    fun signerToken(): Pkcs12SignatureToken {
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("signer", signerKeys.private, password, arrayOf(signerCertificate, caCertificate))
        }
        val bytes = ByteArrayOutputStream().also { keyStore.store(it, password) }.toByteArray()
        return Pkcs12SignatureToken(bytes, KeyStore.PasswordProtection(password))
    }

    override fun close() = server.stop(0)

    private fun timestamp(exchange: HttpExchange) {
        tsaRequests.incrementAndGet()
        val request = TimeStampRequest(exchange.requestBody.readBytes())
        val generator = TimeStampResponseGenerator(tokenGenerator(), TSPAlgorithms.ALLOWED)
        val response = if (kamuSmCustomer != null && !identityValid(exchange.requestHeaders.getFirst("identity"), request)) {
            generator.generateRejectedResponse(TSPException("identity rejected"))
        } else {
            generator.generateGrantedResponse(request, BigInteger.valueOf(serials.incrementAndGet()), Date())
        }
        exchange.reply(200, "application/timestamp-reply", response.encoded)
    }

    /** Decrypts the identity header the way Kamu SM does and compares it with the request's imprint. */
    private fun identityValid(header: String?, request: TimeStampRequest): Boolean {
        val (customer, secret) = kamuSmCustomer ?: return true
        header ?: return false
        val fields = ASN1Sequence.getInstance(BigInteger(header, 16).toByteArray().dropWhile { it == 0.toByte() }.toByteArray())
        val userId = ASN1Integer.getInstance(fields.getObjectAt(0)).value.toLong()
        val salt = ASN1OctetString.getInstance(fields.getObjectAt(1)).octets
        val iterations = ASN1Integer.getInstance(fields.getObjectAt(2)).value.toInt()
        val iv = ASN1OctetString.getInstance(fields.getObjectAt(3)).octets
        val encrypted = ASN1OctetString.getInstance(fields.getObjectAt(4)).octets
        val key = KamuSmIdentity.deriveKey(secret.toCharArray(), salt, iterations)
        val imprint = runCatching {
            Cipher.getInstance("AES/CBC/PKCS5Padding")
                .apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
                .doFinal(encrypted)
        }.getOrNull()
        return userId == customer && imprint.contentEquals(request.messageImprintDigest)
    }

    private fun tokenGenerator(): TimeStampTokenGenerator {
        val digests = JcaDigestCalculatorProviderBuilder().build()
        val signerInfo = JcaSimpleSignerInfoGeneratorBuilder().build("SHA256withRSA", tsaKeys.private, tsaCertificate)
        return TimeStampTokenGenerator(
            signerInfo,
            digests.get(AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256)),
            ASN1ObjectIdentifier("1.2.3.4.1"),
        ).apply { addCertificates(JcaCertStore(listOf(tsaCertificate, caCertificate))) }
    }

    private fun certificate(
        subject: X500Name,
        issuer: X500Name,
        keys: KeyPair,
        issuerKey: PrivateKey,
        ca: Boolean = false,
        extensions: X509v3CertificateBuilder.() -> Unit,
    ): X509Certificate {
        val now = Instant.now()
        val builder = JcaX509v3CertificateBuilder(
            issuer, BigInteger.valueOf(serials.incrementAndGet()),
            Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(365, ChronoUnit.DAYS)),
            subject, keys.public,
        ).addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
        if (!ca) {
            val point = DistributionPoint(
                DistributionPointName(GeneralNames(GeneralName(GeneralName.uniformResourceIdentifier, "$base/ca.crl"))),
                null, null,
            )
            builder.addExtension(Extension.cRLDistributionPoints, false, CRLDistPoint(arrayOf(point)))
        }
        builder.extensions()
        return JcaX509CertificateConverter()
            .getCertificate(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(issuerKey)))
    }

    private fun rsa() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun HttpExchange.reply(status: Int, type: String, body: ByteArray) {
        responseHeaders.add("Content-Type", type)
        sendResponseHeaders(status, body.size.toLong())
        responseBody.use { it.write(body) }
    }

    private companion object {
        val CA_NAME = X500Name("C=TR,O=Local Test,CN=Local Test Root")
    }
}
