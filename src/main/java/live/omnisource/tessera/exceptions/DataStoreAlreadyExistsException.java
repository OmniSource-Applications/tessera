package live.omnisource.tessera.exceptions;

public class DataStoreAlreadyExistsException extends RuntimeException {
    public DataStoreAlreadyExistsException(String name) { super("Data store already exists: " + name); }
}