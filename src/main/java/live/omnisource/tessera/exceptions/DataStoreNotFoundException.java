package live.omnisource.tessera.exceptions;

public class DataStoreNotFoundException extends RuntimeException {
    public DataStoreNotFoundException(String name) { super("Data store not found: " + name); }
}
