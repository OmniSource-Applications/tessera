package live.omnisource.tessera.exceptions;

public class LayerGroupNotFoundException extends RuntimeException {
  public LayerGroupNotFoundException(String name) {
    super("Layer group not found: " + name);
  }
}
