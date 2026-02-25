package live.omnisource.tessera.exceptions;

public class CryptoException extends RuntimeException {
    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
    public CryptoException(String message) {
        super(message);
    }
}
