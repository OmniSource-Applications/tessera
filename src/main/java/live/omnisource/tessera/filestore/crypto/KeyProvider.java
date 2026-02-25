package live.omnisource.tessera.filestore.crypto;

import live.omnisource.tessera.config.ApplicationProperties;
import live.omnisource.tessera.exceptions.CryptoException;

import java.util.Base64;

/**
 * KeyProvider is a utility class responsible for managing and decoding cryptographic keys.
 * It uses a base64-encoded string, referred to as a "salt," to derive a secure key.
 * This class validates the salt and ensures that the resulting key meets the expected length
 * for cryptographic operations.
 * Instances of this class can be created using the {@link #fromProperties(ApplicationProperties.CryptoProperties)}
 * method. The class performs validation during initialization to ensure that a valid salt is provided.
 */
public class KeyProvider {
    private final String salt;

    /**
     * Creates a KeyProvider instance from the provided crypto properties.
     *
     * @param crypto The crypto properties containing the salt.
     * @return A KeyProvider instance if the salt is valid, otherwise throws CryptoException.
     */
    public static KeyProvider fromProperties(ApplicationProperties.CryptoProperties crypto) {
        if (crypto == null || crypto.salt() == null || crypto.salt().isBlank()) {
            throw new CryptoException(
                    "hexgeo.crypto.salt is not configured. " +
                            "Set SIGNING_SALT env var or add hexgeo.crypto.salt to application-dev.yaml. " +
                            "Generate with: openssl rand -base64 32");
        }
        return new KeyProvider(crypto.salt());
    }

    /**
     * Retrieves the master key as a byte array.
     *
     * @return The master key bytes.
     */
    public byte[] getKey() {
        return decode(salt);
    }

    private KeyProvider(String salt) {
        this.salt = salt;
    }

    private static byte[] decode(String base64) {
        final byte[] key = Base64.getDecoder().decode(base64);
        if (key.length != 32) {
            throw new CryptoException(
                    "Master key must be 32 bytes (base64). Got " + key.length + " bytes.");
        }
        return key;
    }
}
