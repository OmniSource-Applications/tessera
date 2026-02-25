package live.omnisource.tessera.filestore.crypto;

import live.omnisource.tessera.exceptions.CryptoException;

import java.util.Objects;

/**
 * Represents a reference to a secret, encapsulating a key with a predefined URI scheme.
 * This class is immutable and provides utility methods for creating and parsing secret references.
 *
 * The reference is always prefixed with the {@code secret://} scheme, which is validated during
 * the creation or parsing of {@code SecretRef} objects.
 */
public record SecretRef(String key) {
    public static final String SCHEME = "secret://";

    /**
     * Creates a new SecretRef instance with the provided key.
     *
     * @param key The secret key, cannot be null or blank.
     * @return A new SecretRef instance.
     * @throws CryptoException if the key is null or blank.
     */
    public static SecretRef of(String key) {
        if (key == null || key.isBlank()) {
            throw new CryptoException("SecretRef key cannot be blank");
        }
        return new SecretRef(key);
    }

    /**
     * Parses a SecretRef from the provided string representation.
     *
     * @param ref The string representation of the SecretRef cannot be null or blank.
     * @return A new SecretRef instance.
     * @throws CryptoException if the ref is null, blank, or does not start with the secret scheme.
     */
    public static SecretRef parse(String ref) {
        if (ref == null || ref.isBlank()) {
            throw new CryptoException("SecretRef cannot be blank");
        }
        if (!ref.startsWith(SCHEME)) {
            throw new CryptoException("Invalid SecretRef (missing scheme): " + ref);
        }
        String key = ref.substring(SCHEME.length());
        return of(key);
    }

    @Override
    public String toString() {
        return SCHEME + key;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SecretRef other)) return false;
        return Objects.equals(this.key, other.key);
    }

}
