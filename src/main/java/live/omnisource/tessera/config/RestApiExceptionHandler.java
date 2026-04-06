package live.omnisource.tessera.config;

import live.omnisource.tessera.exceptions.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Catches exceptions from @RestController classes and returns
 * consistent JSON error responses.
 *
 * <p>Only applies to controllers in the api packages (where
 * RestControllers live). The Thymeleaf web controllers use their
 * own redirect/flash-attribute error handling.</p>
 */
@Slf4j
@RestControllerAdvice(basePackages = {
        "live.omnisource.tessera.workspace.api",
        "live.omnisource.tessera.datastore.api",
        "live.omnisource.tessera.layer.api",
        "live.omnisource.tessera.sync.api",
        "live.omnisource.tessera.stream.api",
        "live.omnisource.tessera.catalog.api",
        "live.omnisource.tessera.apikey.api",
        "live.omnisource.tessera.security.rbac.api",
        "live.omnisource.tessera.layergroup.api",
        "live.omnisource.tessera.system.api",
        "live.omnisource.tessera.demo"
})
public class RestApiExceptionHandler {

    @ExceptionHandler(WorkspaceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleWorkspaceNotFound(WorkspaceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "WORKSPACE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(WorkspaceAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleWorkspaceExists(WorkspaceAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "WORKSPACE_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(WorkspaceValidationException.class)
    public ResponseEntity<Map<String, Object>> handleWorkspaceValidation(WorkspaceValidationException ex) {
        return error(HttpStatus.BAD_REQUEST, "WORKSPACE_VALIDATION", ex.getMessage());
    }

    @ExceptionHandler(DataStoreNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDataStoreNotFound(DataStoreNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "DATASTORE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(DataStoreAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleDataStoreExists(DataStoreAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "DATASTORE_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(DataStoreValidationException.class)
    public ResponseEntity<Map<String, Object>> handleDataStoreValidation(DataStoreValidationException ex) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(LayerGroupNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleLayerGroupNotFound(LayerGroupNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "LAYER_GROUP_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(LayerGroupAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleLayerGroupExists(LayerGroupAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, "LAYER_GROUP_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled API exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", code,
                "message", message != null ? message : "",
                "status", status.value(),
                "timestamp", Instant.now().toString()
        ));
    }
}