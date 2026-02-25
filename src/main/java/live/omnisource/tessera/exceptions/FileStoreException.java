package live.omnisource.tessera.exceptions;

public class FileStoreException extends RuntimeException {
    public FileStoreException(String message, Throwable cause) {
        super(message, cause);
    }
    public FileStoreException(String message) {
        super(message);
    }
}
