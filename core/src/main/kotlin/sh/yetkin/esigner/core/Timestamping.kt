package sh.yetkin.esigner.core

import eu.europa.esig.dss.enumerations.DigestAlgorithm
import eu.europa.esig.dss.model.TimestampBinary
import eu.europa.esig.dss.spi.x509.tsp.TSPSource
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.tsp.TimeStampRequestGenerator
import org.bouncycastle.tsp.TimeStampResponse
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * How a time-stamping authority authenticates requests. Passwords are char arrays owned by the caller,
 * which zeroes them with [clear] when they are no longer needed. They are never logged or written out.
 */
sealed interface TimestampAuth {
    fun clear() {}

    /** A public or IP-restricted TSA. */
    data object None : TimestampAuth

    /** HTTP Basic authentication, used by several commercial TSAs. */
    class Basic(val user: String, val password: CharArray) : TimestampAuth {
        override fun clear() = password.fill('\u0000')
        override fun toString() = "Basic(user=$user)"
    }

    /**
     * Kamu SM: an `identity` header carrying the customer number and the message imprint encrypted with
     * a key derived from the customer password (see [KamuSmIdentity]).
     */
    class KamuSm(val customerNumber: Long, val password: CharArray, val iterations: Int = 100) : TimestampAuth {
        override fun clear() = password.fill('\u0000')
        override fun toString() = "KamuSm(customerNumber=$customerNumber)"
    }
}

/**
 * RFC 3161 client over HTTP, for DSS. Each call sends one request with a fresh nonce and checks that
 * the response matches it. Failures become [SigningException]s with timestamp error codes.
 */
class HttpTimestampSource(
    private val url: String,
    private val auth: TimestampAuth = TimestampAuth.None,
    /** Optional TSA policy OID, if the provider requires one. */
    private val policyOid: String? = null,
    private val timeout: Duration = Duration.ofSeconds(20),
) : TSPSource {
    private val client = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
    private val random = SecureRandom()

    override fun getTimeStampResponse(digestAlgorithm: DigestAlgorithm, digest: ByteArray): TimestampBinary {
        val generator = TimeStampRequestGenerator().apply {
            setCertReq(true)
            policyOid?.let { setReqPolicy(ASN1ObjectIdentifier(it)) }
        }
        val nonce = BigInteger(64, random)
        val request = generator.generate(ASN1ObjectIdentifier(digestAlgorithm.oid), digest, nonce)

        val uri = try {
            URI(url.trim()).also { require(it.scheme == "http" || it.scheme == "https") }
        } catch (e: Exception) {
            throw SigningException(SigningError.TIMESTAMP_UNREACHABLE, url, e)
        }
        val builder = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("Content-Type", "application/timestamp-query")
            .POST(HttpRequest.BodyPublishers.ofByteArray(request.encoded))
        when (auth) {
            is TimestampAuth.None -> Unit
            is TimestampAuth.Basic -> builder.header("Authorization", basicHeader(auth))
            is TimestampAuth.KamuSm -> builder.header(
                KamuSmIdentity.HEADER,
                KamuSmIdentity.build(auth.customerNumber, auth.password, digest, auth.iterations, random),
            )
        }

        val response = try {
            client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        } catch (e: Exception) {
            throw SigningException(SigningError.TIMESTAMP_UNREACHABLE, url, e)
        }
        when (response.statusCode()) {
            200 -> Unit
            401, 403 -> throw SigningException(SigningError.TIMESTAMP_REJECTED, "HTTP ${response.statusCode()}", TsaResponseError(response))
            else -> throw SigningException(SigningError.TIMESTAMP_FAILED, "HTTP ${response.statusCode()}", TsaResponseError(response))
        }

        val timestamp = try {
            TimeStampResponse(response.body())
        } catch (e: Exception) {
            throw SigningException(SigningError.TIMESTAMP_FAILED, cause = TsaResponseError(response, e))
        }
        val token = timestamp.timeStampToken
            // Kamu SM answers a wrong password or an empty balance with a rejection status.
            ?: throw SigningException(
                SigningError.TIMESTAMP_REJECTED,
                timestamp.statusString ?: "status ${timestamp.status}",
                TsaResponseError(response, note = "PKIStatus ${timestamp.status}, failInfo ${timestamp.failInfo?.intValue() ?: "-"}"),
            )
        try {
            timestamp.validate(request)
        } catch (e: Exception) {
            throw SigningException(SigningError.TIMESTAMP_FAILED, cause = TsaResponseError(response, e))
        }
        return TimestampBinary(token.encoded)
    }

    private fun basicHeader(auth: TimestampAuth.Basic): String {
        val user = auth.user.toByteArray(Charsets.UTF_8)
        val password = Charsets.UTF_8.encode(java.nio.CharBuffer.wrap(auth.password))
        val raw = ByteArray(user.size + 1 + password.remaining())
        user.copyInto(raw)
        raw[user.size] = ':'.code.toByte()
        password.get(raw, user.size + 1, password.remaining())
        if (password.hasArray()) password.array().fill(0)
        return try {
            "Basic " + Base64.getEncoder().encodeToString(raw)
        } finally {
            raw.fill(0)
        }
    }
}

/**
 * The Kamu SM `identity` header: hex of the DER encoding of
 * `SEQUENCE { userId INTEGER, salt OCTET STRING, iterationCount INTEGER, iv OCTET STRING, encryptedMessageImprint OCTET STRING }`,
 * where the hashed message of the request is AES-256-CBC (PKCS#7 padding) encrypted with a PBKDF2-HMAC-SHA256
 * key from the customer password. The salt is the IV.
 */
object KamuSmIdentity {
    const val HEADER = "identity"

    fun build(customerNumber: Long, password: CharArray, messageImprint: ByteArray, iterations: Int, random: SecureRandom): String {
        val iv = ByteArray(16).also(random::nextBytes)
        val salt = iv.copyOf()
        val key = deriveKey(password, salt, iterations)
        val encrypted = try {
            Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            }.doFinal(messageImprint)
        } finally {
            key.fill(0)
        }
        val sequence = DERSequence(
            ASN1EncodableVector().apply {
                add(ASN1Integer(customerNumber))
                add(DEROctetString(salt))
                add(ASN1Integer(iterations.toLong()))
                add(DEROctetString(iv))
                add(DEROctetString(encrypted))
            },
        )
        // DER starts with 0x30, so this matches the reference encoding (hex of the bytes as a big integer).
        return sequence.encoded.joinToString("") { "%02x".format(it) }
    }

    internal fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/**
 * What the TSA actually answered, for the error details view: request line, status, content type and
 * the start of the body (text as is, binary as hex). The request carries no secrets, so nothing is masked.
 */
internal class TsaResponseError(response: HttpResponse<ByteArray>, cause: Throwable? = null, note: String? = null) :
    Exception(describe(response, note), cause) {
    private companion object {
        fun describe(response: HttpResponse<ByteArray>, note: String?): String {
            val type = response.headers().firstValue("Content-Type").orElse("-")
            val body = response.body()
            val isText = listOf("text", "json", "xml", "html").any { it in type.lowercase() }
            val preview = if (isText) {
                String(body, Charsets.UTF_8).take(2000)
            } else {
                body.take(64).joinToString(" ") { "%02x".format(it) } + if (body.size > 64) " ..." else ""
            }
            return listOfNotNull(
                "${response.request().method()} ${response.uri()} -> HTTP ${response.statusCode()}",
                "Content-Type: $type, ${body.size} bytes",
                note,
                preview.ifBlank { null },
            ).joinToString("\n")
        }
    }
}
