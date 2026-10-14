package sh.yetkin.esigner.core

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1String
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import java.security.cert.X509Certificate
import java.time.Instant

/** Display-safe summary of a signing certificate. The national id is only kept masked. */
data class CertificateInfo(
    val commonName: String,
    val maskedNationalId: String?,
    val issuerName: String,
    val notBefore: Instant,
    val notAfter: Instant,
    /** Key usage includes nonRepudiation, i.e. a (qualified) signing certificate. */
    val isSigningCertificate: Boolean,
) {
    fun isValidAt(time: Instant): Boolean = !time.isBefore(notBefore) && !time.isAfter(notAfter)

    companion object {
        private const val NON_REPUDIATION = 1

        fun from(cert: X509Certificate): CertificateInfo {
            val subject = X500Name.getInstance(cert.subjectX500Principal.encoded)
            val issuer = X500Name.getInstance(cert.issuerX500Principal.encoded)
            return CertificateInfo(
                commonName = subject.first(BCStyle.CN) ?: subject.toString(),
                maskedNationalId = subject.first(BCStyle.SERIALNUMBER)?.let(::maskNationalId),
                issuerName = issuer.first(BCStyle.CN) ?: issuer.first(BCStyle.O) ?: issuer.toString(),
                notBefore = cert.notBefore.toInstant(),
                notAfter = cert.notAfter.toInstant(),
                isSigningCertificate = cert.keyUsage?.getOrNull(NON_REPUDIATION) == true,
            )
        }

        private fun X500Name.first(oid: ASN1ObjectIdentifier): String? =
            getRDNs(oid).firstOrNull()?.first?.value?.let { (it as? ASN1String)?.string ?: it.toString() }
    }
}

/** Keeps the first two and last two characters: 12345678901 -> 12*******01. Short values are fully masked. */
fun maskNationalId(id: String): String {
    val value = id.trim()
    if (value.length <= 4) return "*".repeat(value.length)
    return value.take(2) + "*".repeat(value.length - 4) + value.takeLast(2)
}
