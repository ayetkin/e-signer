import eu.europa.esig.dss.cades.CAdESSignatureParameters
import eu.europa.esig.dss.cades.signature.CAdESService
import eu.europa.esig.dss.enumerations.DigestAlgorithm
import eu.europa.esig.dss.enumerations.SignatureLevel
import eu.europa.esig.dss.enumerations.SignaturePackaging
import eu.europa.esig.dss.model.FileDocument
import eu.europa.esig.dss.pades.PAdESSignatureParameters
import eu.europa.esig.dss.pades.signature.PAdESService
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import eu.europa.esig.dss.token.DSSPrivateKeyEntry
import eu.europa.esig.dss.token.PasswordInputCallback
import eu.europa.esig.dss.token.Pkcs11SignatureToken
import java.io.File
import kotlin.system.exitProcess

fun defaultLibPath(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("mac") -> "/usr/local/lib/libakisp11.dylib"
        os.contains("win") -> "C:\\Windows\\System32\\akisp11.dll"
        else -> "/usr/lib/libakisp11.so"
    }
}

fun readPin(): CharArray {
    val console = System.console()
    if (console != null) return console.readPassword("AKİS PIN: ")
    print("AKİS PIN (ekranda görünecek): ")
    return (readLine() ?: "").toCharArray()
}

fun subjectOf(key: DSSPrivateKeyEntry): String =
    key.certificate.certificate.subjectX500Principal.name

/** PDF içine PAdES imzası (mevcut imzalar korunur, artımlı güncelleme). */
fun signPades(input: File, token: Pkcs11SignatureToken, key: DSSPrivateKeyEntry): File {
    val doc = FileDocument(input)
    val params = PAdESSignatureParameters().apply {
        signatureLevel = SignatureLevel.PAdES_BASELINE_B
        digestAlgorithm = DigestAlgorithm.SHA256
        signingCertificate = key.certificate
        certificateChain = key.certificateChain.toList()
    }
    val service = PAdESService(CommonCertificateVerifier())
    val toSign = service.getDataToSign(doc, params)
    val sigValue = token.sign(toSign, params.digestAlgorithm, key)
    val signed = service.signDocument(doc, params, sigValue)
    val out = File(input.parentFile, input.nameWithoutExtension + "-imzali.pdf")
    signed.save(out.absolutePath)
    return out
}

/** Herhangi bir dosyayı içine alan (enveloping) CAdES imzası -> .imz */
fun signCades(input: File, token: Pkcs11SignatureToken, key: DSSPrivateKeyEntry): File {
    val doc = FileDocument(input)
    val params = CAdESSignatureParameters().apply {
        signatureLevel = SignatureLevel.CAdES_BASELINE_B
        signaturePackaging = SignaturePackaging.ENVELOPING
        digestAlgorithm = DigestAlgorithm.SHA256
        signingCertificate = key.certificate
        certificateChain = key.certificateChain.toList()
    }
    val service = CAdESService(CommonCertificateVerifier())
    val toSign = service.getDataToSign(doc, params)
    val sigValue = token.sign(toSign, params.digestAlgorithm, key)
    val signed = service.signDocument(doc, params, sigValue)
    val out = File(input.parentFile, input.name + ".imz")
    signed.save(out.absolutePath)
    return out
}

fun main(args: Array<String>) {
    if (args.size < 2 || args[0] !in setOf("pades", "cades")) {
        println("Kullanım:")
        println("  eimza-test pades <dosya.pdf>     -> <dosya>-imzali.pdf")
        println("  eimza-test cades <herhangi-dosya> -> <dosya>.imz")
        println("Kütüphane yolu farklıysa: AKIS_LIB=/yol/libakisp11.dylib eimza-test ...")
        exitProcess(1)
    }
    val mode = args[0]
    val input = File(args[1])
    if (!input.isFile) {
        println("Dosya bulunamadı: ${input.absolutePath}")
        exitProcess(1)
    }

    val lib = System.getenv("AKIS_LIB") ?: defaultLibPath()
    println("PKCS#11 kütüphanesi: $lib")

    // PIN tek sefer sorulur; hata olursa tekrar denenmez (bloke riskine karşı).
    val pin = readPin()
    // Slot'u ID ile değil sırasıyla seç (AKİS'te ilk slot'un ID'si 0 değil 1).
    val slotIndex = System.getenv("AKIS_SLOT_INDEX")?.toIntOrNull() ?: 0
    val token = Pkcs11SignatureToken(lib, PasswordInputCallback { pin }, -1, slotIndex, "")
    try {
        val keys = token.keys
        if (keys.isEmpty()) {
            println("Kartta imza anahtarı bulunamadı.")
            exitProcess(2)
        }
        keys.forEachIndexed { i, k -> println("Anahtar [$i]: ${subjectOf(k)}") }
        val key = keys.first()
        println("Kullanılan: ${subjectOf(key)}")

        val out = when (mode) {
            "pades" -> signPades(input, token, key)
            else -> signCades(input, token, key)
        }
        println("Başarılı. İmzalı dosya: ${out.absolutePath}")
    } catch (e: Exception) {
        println("HATA: ${e.javaClass.simpleName}: ${e.message}")
        var c = e.cause
        while (c != null) {
            println("  sebep: ${c.javaClass.simpleName}: ${c.message}")
            c = c.cause
        }
        println("PIN'i tekrar denemeden önce bu çıktıyı paylaş.")
        exitProcess(3)
    } finally {
        token.close()
        pin.fill('0')
    }
}
