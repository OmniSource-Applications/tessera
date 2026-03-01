package live.omnisource.tessera.exceptions;

public class LayerGroupAlreadyExistsException extends RuntimeException {
    public LayerGroupAlreadyExistsException(String name) {
        super("Layer group already exists: " + name);
    }
}
