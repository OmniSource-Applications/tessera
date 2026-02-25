package live.omnisource.tessera.filestore.crypto;

/**
 * Utility class for encoding byte arrays into their hexadecimal string representation.
 * The class is designed to be used for converting raw byte data into a human-readable
 * hexadecimal format, commonly used in cryptographic operations and data encoding.
 * This class is non-instantiable and only provides static utility methods.
 */
public class Hex {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Hex() {}

    /**
     * Converts a byte array into its hexadecimal string representation.
     *
     * @param bytes the byte array to be converted; must not be null
     * @return a string representing the hexadecimal format of the input byte array
     */
    public static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
