package sh.yetkin.esigner.core;

import java.util.ArrayList;
import java.util.List;
import sun.security.pkcs11.wrapper.CK_ATTRIBUTE;
import sun.security.pkcs11.wrapper.CK_C_INITIALIZE_ARGS;
import sun.security.pkcs11.wrapper.CK_SLOT_INFO;
import sun.security.pkcs11.wrapper.CK_TOKEN_INFO;
import sun.security.pkcs11.wrapper.PKCS11;
import sun.security.pkcs11.wrapper.PKCS11Constants;

/**
 * Lists readers, inserted tokens and their public certificates without logging in, using the JDK's
 * internal PKCS#11 wrapper (the same module instance SunPKCS11 uses later for signing).
 *
 * Needs {@code --add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED} at compile and run time.
 * Not thread safe: call from the single card thread.
 */
final class Pkcs11Scanner {
    private Pkcs11Scanner() {
    }

    record RawToken(
            long slotId,
            String readerName,
            String label,
            String model,
            long flags,
            List<byte[]> certificates) {
    }

    record RawScan(int readerCount, List<RawToken> tokens) {
    }

    static RawScan scan(String libraryPath) throws Exception {
        CK_C_INITIALIZE_ARGS initArgs = new CK_C_INITIALIZE_ARGS();
        // Same flags as SunPKCS11; the module is initialized once per path and shared with it.
        initArgs.flags = PKCS11Constants.CKF_OS_LOCKING_OK;
        PKCS11 p11 = PKCS11.getInstance(libraryPath, "C_GetFunctionList", initArgs, false);

        int readerCount = p11.C_GetSlotList(false).length;
        List<RawToken> tokens = new ArrayList<>();
        for (long slot : p11.C_GetSlotList(true)) {
            try {
                CK_SLOT_INFO slotInfo = p11.C_GetSlotInfo(slot);
                CK_TOKEN_INFO tokenInfo = p11.C_GetTokenInfo(slot);
                tokens.add(new RawToken(
                        slot,
                        text(slotInfo.slotDescription),
                        text(tokenInfo.label),
                        text(tokenInfo.model),
                        tokenInfo.flags,
                        readCertificates(p11, slot)));
            } catch (Exception e) {
                // Card removed while reading, or a slot the module cannot describe: skip it.
            }
        }
        return new RawScan(readerCount, tokens);
    }

    private static List<byte[]> readCertificates(PKCS11 p11, long slot) {
        List<byte[]> result = new ArrayList<>();
        long session;
        try {
            session = p11.C_OpenSession(slot, PKCS11Constants.CKF_SERIAL_SESSION, null, null);
        } catch (Exception e) {
            return result;
        }
        try {
            p11.C_FindObjectsInit(session, new CK_ATTRIBUTE[] {
                    new CK_ATTRIBUTE(PKCS11Constants.CKA_CLASS, PKCS11Constants.CKO_CERTIFICATE)});
            long[] objects;
            try {
                objects = p11.C_FindObjects(session, 32);
            } finally {
                p11.C_FindObjectsFinal(session);
            }
            for (long object : objects) {
                CK_ATTRIBUTE[] value = {new CK_ATTRIBUTE(PKCS11Constants.CKA_VALUE)};
                p11.C_GetAttributeValue(session, object, value);
                byte[] bytes = value[0].getByteArray();
                if (bytes != null) {
                    result.add(bytes);
                }
            }
        } catch (Exception e) {
            // Certificates are optional here; they are read again after login.
        } finally {
            try {
                p11.C_CloseSession(session);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private static String text(char[] padded) {
        return padded == null ? "" : new String(padded).trim();
    }
}
