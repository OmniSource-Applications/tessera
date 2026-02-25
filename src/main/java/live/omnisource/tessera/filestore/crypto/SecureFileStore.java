package live.omnisource.tessera.filestore.crypto;

import live.omnisource.tessera.exceptions.CryptoException;
import live.omnisource.tessera.filestore.FileStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * SecureFileStore provides secure file storage and retrieval using AES-GCM encryption.
 */
public class SecureFileStore {
    private static final Logger log = LoggerFactory.getLogger(SecureFileStore.class);

    private static final byte[] MAGIC = new byte[] { 'H', '3', 'G', 'T' };
    private static final byte VERSION = 0x01;

    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_LEN = 12;
    private static final int DEK_LEN = 32;

    private final Path secretsDir;
    private final SecureRandom rng;
    private final byte[] masterKey32;
    private final FileStoreService fileStoreService;

    /**
     * Constructs an instance of the SecureFileStore class for managing encrypted file storage.
     *
     * @param secretsDir The base directory where encrypted files and related metadata
     *                   will be stored. Must not be null.
     * @param rng An instance of {@code SecureRandom} used for generating cryptographic
     *            nonces and ensuring randomness for security purposes. Must not be null.
     * @param masterKey32 A 32-byte master key used for securing derived encryption keys.
     *                    The key must have exactly 32 bytes. Must not be null.
     * @param fileStoreService An instance of {@code FileStoreService} used for managing
     *                         file system operations such as directory creation and
     *                         atomic writes. Must not be null.
     * @throws CryptoException If {@code masterKey32} is null or does not have a length of 32 bytes.
     */
    public SecureFileStore(Path secretsDir, SecureRandom rng, byte[] masterKey32, FileStoreService fileStoreService) {
        this.secretsDir = secretsDir.toAbsolutePath().normalize();
        this.rng = rng;
        this.masterKey32 = requireLen(masterKey32, DEK_LEN, "masterKey32");
        this.fileStoreService = fileStoreService;
    }

    /**
     * Encrypts the provided plaintext using a data encryption key (DEK) and stores
     * the resulting envelope in the underlying file storage system. The DEK is
     * secured using AES-GCM encryption with a master key and associated data (AAD).
     * The method ensures secure storage of both the encrypted data and the key
     * material for retrieval and decryption.
     *
     * @param key The unique identifier associated with the plaintext to be stored.
     *            Must not be null or blank.
     * @param plaintext The plaintext data to encrypt and store. Must not be null.
     *                  If null, a {@code CryptoException} is thrown.
     * @return A string reference pointing to the stored secret, which can later be used
     *         for retrieval or deletion operations.
     * @throws CryptoException If the plaintext is null, the key is blank, or if there are
     *                         errors during the encryption or storage process.
     */
    public String put(String key, byte[] plaintext) {
        requireNonBlank(key);
        if (plaintext == null) {
            log.error("plaintext cannot be null");
            throw new CryptoException("plaintext cannot be null");
        }

        try {
            Files.createDirectories(secretsDir);

            byte[] dek = new byte[DEK_LEN];
            rng.nextBytes(dek);

            byte[] nonce1 = new byte[NONCE_LEN];
            rng.nextBytes(nonce1);

            byte[] aad = aadFor(key, VERSION);
            byte[] ciphertext = aesGcmEncrypt(dek, nonce1, aad, plaintext);

            byte[] nonce2 = new byte[NONCE_LEN];
            rng.nextBytes(nonce2);

            byte[] wrappedDek = aesGcmEncrypt(masterKey32, nonce2, aad, dek);
            byte[] envelope = encodeEnvelope(nonce2, wrappedDek, nonce1, ciphertext);

            Path file = fileForKey(key);

            if (fileStoreService != null) {
                fileStoreService.writeAtomic(file, envelope);
            } else {
                Files.write(file, envelope);
            }

            Arrays.fill(dek, (byte) 0);

            return SecretRef.of(key).toString();
        } catch (IOException e) {
            log.error("Failed to create secrets directory", e);
            throw new CryptoException("Failed to create secrets directory", e);
        }
    }

    /**
     * Retrieve a secret value by its key.
     *
     * @param key The secret key to retrieve
     * @return The decrypted secret value
     */
    public byte[] get(String key) {
        requireNonBlank(key);
        Path file = fileForKey(key);

        log.debug("Resolved secret key '{}' to file '{}'", key, file);

        if (!Files.exists(file)) {
            log.debug("Secret not found for key={}", key);
            throw new CryptoException("Secret not found for key=" + key);
        }

        try {
            byte[] envelope = Files.readAllBytes(file);
            Decoded d = decodeEnvelope(envelope);

            byte[] aad = aadFor(key, d.version);

            byte[] dek = aesGcmDecrypt(masterKey32, d.nonce2, aad, d.wrappedDek);

            try {
                log.debug("Successfully decrypted secret for key={}", key);
                return aesGcmDecrypt(dek, d.nonce1, aad, d.ciphertext);
            } finally {
                Arrays.fill(dek, (byte) 0);
            }
        } catch (IOException e) {
            log.debug("Failed to read secret for key={}", key, e);
            throw new CryptoException("Failed to read secret for key=" + key, e);
        }
    }

    /**
     * Retrieves the secret value associated with the provided {@code SecretRef}.
     *
     * This method resolves the key contained within the {@code SecretRef},
     * validates the reference, and fetches the corresponding decrypted secret value
     * from the store. If the {@code SecretRef} is null, a {@code CryptoException}
     * is thrown.
     *
     * @param ref The {@code SecretRef} containing the key of the secret to retrieve.
     *            Must not be null.
     * @return A byte array representing the decrypted secret value.
     * @throws CryptoException If the {@code SecretRef} is null or an error occurs during retrieval.
     */
    public byte[] get(SecretRef ref) {
        if (ref == null) {
            throw new CryptoException("SecretRef must not be null");
        }
        return get(ref.key());
    }

    /**
     * Deletes the secret associated with the provided key.
     *
     * @param key The key of the secret to delete. Must not be blank.
     * @throws CryptoException If the key is blank or an error occurs during deletion.
     */
    public void delete(String key) {
        requireNonBlank(key);
        Path file = fileForKey(key);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.debug("Failed to delete secret for key={}", key, e);
            throw new CryptoException("Failed to delete secret for key=" + key, e);
        }
    }

    /**
     * Retrieves the secret associated with the provided reference string.
     *
     * @param refString The reference string of the secret to retrieve. Must not be blank.
     * @return The secret bytes, or null if not found.
     * @throws CryptoException If the reference string is blank or an error occurs during retrieval.
     */
    public byte[] getRefString(String refString) {
        return get(SecretRef.parse(refString));
    }

    /**
     * Checks if a secret exists for the given key.
     *
     * @param key The key to check. Must not be blank.
     * @return true if the secret exists, false otherwise.
     * @throws CryptoException If the key is blank.
     */
    public boolean exists(String key) {
        requireNonBlank(key);
        return Files.exists(fileForKey(key));
    }

    private static byte[] requireLen(byte[] b, int len, String name) {
        if (b == null) throw new CryptoException(name + " cannot be null");
        if (b.length != len) throw new CryptoException(name + " must be " + len + " bytes; got " + b.length);
        return b;
    }

    private static void requireNonBlank(String s) {
        if (s == null || s.isBlank()) throw new CryptoException("key" + " cannot be blank");
    }

    Path fileForKey(String key) {
        String hex = sha256Hex(key);
        return secretsDir.resolve(hex + ".bin");
    }

    private static byte[] encodeEnvelope(byte[] nonce2, byte[] wrappedDek, byte[] nonce1, byte[] ciphertext) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);

            out.write(MAGIC);
            out.writeByte(VERSION);

            out.writeByte(nonce2.length);
            out.write(nonce2);

            out.writeInt(wrappedDek.length);
            out.write(wrappedDek);

            out.writeByte(nonce1.length);
            out.write(nonce1);

            out.writeInt(ciphertext.length);
            out.write(ciphertext);

            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            log.debug("Failed to encode envelope", e);
            throw new CryptoException("Failed to encode envelope", e);
        }
    }

    private static Decoded decodeEnvelope(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
            byte[] magic = in.readNBytes(4);
            if (magic.length != 4 || !Arrays.equals(magic, MAGIC)) {
                log.debug("Unsupported secret format (bad magic)");
                throw new CryptoException("Unsupported secret format (bad magic)");
            }

            byte version = in.readByte();
            if (version != VERSION) {
                log.debug("Unsupported secret format (version " + version + ")");
                throw new CryptoException("Unsupported secret format (version " + version + ")");
            }

            int nonce2Len = Byte.toUnsignedInt(in.readByte());
            byte[] nonce2 = in.readNBytes(nonce2Len);
            requireLen(nonce2, nonce2Len, "nonce2");

            int wrappedDekLen = in.readInt();
            if (wrappedDekLen <= 0) {
                log.debug("Invalid wrappedDek length");
                throw new CryptoException("Invalid wrappedDek length");
            }
            byte[] wrappedDek = in.readNBytes(wrappedDekLen);
            requireLen(wrappedDek, wrappedDekLen, "wrappedDek");

            int nonce1Len = Byte.toUnsignedInt(in.readByte());
            byte[] nonce1 = in.readNBytes(nonce1Len);
            requireLen(nonce1, nonce1Len, "nonce1");

            int ciphertextLen = in.readInt();
            if (ciphertextLen < 0) throw new CryptoException("Invalid ciphertext length");
            byte[] ciphertext = in.readNBytes(ciphertextLen);
            requireLen(ciphertext, ciphertextLen, "ciphertext");

            return new Decoded(version, nonce2, wrappedDek, nonce1, ciphertext);
        } catch (IOException e) {
            log.debug("Failed to decode envelope", e);
            throw new CryptoException("Failed to decode envelope", e);
        }
    }

    private static byte[] aesGcmEncrypt(byte[] key32, byte[] nonce12, byte[] aad, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKey key = new SecretKeySpec(requireLen(key32, 32, "key32"), "AES");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, requireLen(nonce12, NONCE_LEN, "nonce12"));
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            log.debug("AES-GCM encrypt failed", e);
            throw new CryptoException("AES-GCM encrypt failed", e);
        }
    }

    private static byte[] aesGcmDecrypt(byte[] key32, byte[] nonce12, byte[] aad, byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKey key = new SecretKeySpec(requireLen(key32, 32, "key32"), "AES");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, requireLen(nonce12, NONCE_LEN, "nonce12"));
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("AES-GCM decrypt failed (integrity check failed)", e);
        }
    }

    private static byte[] aadFor(String key, byte version) {
        String s = "key=" + key + "\nver=" + (version & 0xFF) + "\n";
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256Hex(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(key.getBytes(StandardCharsets.UTF_8));
            return Hex.toHex(hash);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("SHA-256 not available", e);
        }
    }

    private record Decoded(byte version, byte[] nonce2, byte[] wrappedDek, byte[] nonce1, byte[] ciphertext) {}
}
