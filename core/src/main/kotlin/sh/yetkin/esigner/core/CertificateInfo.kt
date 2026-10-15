package sh.yetkin.esigner.core

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1String
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.CertificatePolicies
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.qualified.QCStatement
import java.security.cert.X509Certificate
import java.time.Instant

/** What the subject serial number is: a person's national id (11 digits) or a company's tax id (10 digits). */
enum class IdType { NATIONAL_ID, TAX_ID, OTHER }

/** Display-safe summary of a signing certificate. The subject id is only kept masked. */
data class CertificateInfo(
    val commonName: String,
    val maskedNationalId: String?,
    val issuerName: String,
    val notBefore: Instant,
    val notAfter: Instant,
    /** Key usage includes nonRepudiation, i.e. a (qualified) signing certificate. */
    val isSigningCertificate: Boolean,
    /** Carries the Turkish QC statement: a qualified electronic certificate under Law 5070. */
    val isQualified5070: Boolean = false,
    /** Issued under the Kamu SM fiscal seal (mali muhur) policy, Tax Procedure Law communique 509. */
    val isFiscalSeal: Boolean = false,
    val idType: IdType = IdType.NATIONAL_ID,
) {
    fun isValidAt(time: Instant): Boolean = !time.isBefore(notBefore) && !time.isAfter(notAfter)

    companion object {
        private const val NON_REPUDIATION = 1

        /**
         * Index of the certificate to sign with: a valid nonRepudiation one, then a valid fiscal seal (which has no
         * nonRepudiation), then the same ignoring validity. -1 when none qualifies.
         */
        fun preferredSigner(certificates: List<CertificateInfo>, now: Instant = Instant.now()): Int =
            listOf<(CertificateInfo) -> Boolean>(
                { it.isSigningCertificate && it.isValidAt(now) },
                { it.isFiscalSeal && it.isValidAt(now) },
                { it.isSigningCertificate },
                { it.isFiscalSeal },
            ).firstNotNullOfOrNull { rule -> certificates.indexOfFirst(rule).takeIf { it >= 0 } } ?: -1

        fun from(cert: X509Certificate): CertificateInfo {
            val subject = X500Name.getInstance(cert.subjectX500Principal.encoded)
            val issuer = X500Name.getInstance(cert.issuerX500Principal.encoded)
            val serial = subject.first(BCStyle.SERIALNUMBER)?.trim()
            return CertificateInfo(
                commonName = subject.first(BCStyle.CN) ?: subject.toString(),
                maskedNationalId = serial?.let(::maskNationalId),
                issuerName = issuer.first(BCStyle.CN) ?: issuer.first(BCStyle.O) ?: issuer.toString(),
                notBefore = cert.notBefore.toInstant(),
                notAfter = cert.notAfter.toInstant(),
                isSigningCertificate = cert.keyUsage?.getOrNull(NON_REPUDIATION) == true,
                isQualified5070 = QC_5070 in qcStatementIds(cert),
                isFiscalSeal = FISCAL_SEAL_POLICY in policyIds(cert),
                idType = when {
                    serial == null || !serial.all(Char::isDigit) -> IdType.OTHER
                    serial.length == 11 -> IdType.NATIONAL_ID
                    serial.length == 10 -> IdType.TAX_ID
                    else -> IdType.OTHER
                },
            )
        }

        /** Kamu SM "Elektronik Mali Muhur Sertifika Ilkeleri" policy OID. */
        const val FISCAL_SEAL_POLICY = "2.16.792.1.2.1.1.5.7.4.1"

        fun policyIds(cert: X509Certificate): Set<String> {
            val value = cert.getExtensionValue(Extension.certificatePolicies.id) ?: return emptySet()
            return runCatching {
                CertificatePolicies.getInstance(ASN1OctetString.getInstance(value).octets)
                    .policyInformation.map { it.policyIdentifier.id }.toSet()
            }.getOrDefault(emptySet())
        }

        /** OID of the QC statement Turkish providers put in qualified certificates (Law 5070). */
        const val QC_5070 = "2.16.792.1.61.0.1.5070.1.1"

        fun qcStatementIds(cert: X509Certificate): Set<String> {
            val value = cert.getExtensionValue(Extension.qCStatements.id) ?: return emptySet()
            return runCatching {
                val statements = ASN1Sequence.getInstance(ASN1OctetString.getInstance(value).octets)
                statements.map { QCStatement.getInstance(it).statementId.id }.toSet()
            }.getOrDefault(emptySet())
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
