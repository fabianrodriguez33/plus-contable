package com.pluscontable.tributario.config;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, String>> validation(ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> beanValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", msg));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> constraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", msg));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensaje", e.getMessage()));
    }

    /** Restricciones de unicidad conocidas -> mensaje claro; cualquier otra -> mensaje generico (409). */
    private static final Map<String, String> UNICIDAD = Map.of(
            "uq_trabajadores_cliente_documento", "Ya existe un trabajador con ese tipo y número de documento en este cliente",
            "uq_documentos_cliente_tipo_version", "Ya existe esa versión del documento para el cliente; vuelva a intentarlo",
            "clientes_ruc_key", "Ya existe un cliente registrado con ese RUC");

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integridad(DataIntegrityViolationException e) {
        String restriccion = e.getCause() instanceof org.hibernate.exception.ConstraintViolationException c
                ? c.getConstraintName() : null;
        String msg = UNICIDAD.getOrDefault(restriccion == null ? "" : restriccion,
                "La operación viola una restricción de integridad de los datos");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensaje", msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> cuerpoIlegible(HttpMessageNotReadableException e) {
        String msg = "El cuerpo de la solicitud no es un JSON válido o está vacío";
        if (e.getCause() instanceof InvalidFormatException f) {
            String campo = f.getPath().isEmpty() ? "?" : f.getPath().get(f.getPath().size() - 1).getFieldName();
            msg = "Valor inválido para '" + campo + "': '" + f.getValue() + "'";
            if (f.getTargetType() != null && f.getTargetType().isEnum()) {
                msg += ". Valores permitidos: " + Arrays.stream(f.getTargetType().getEnumConstants())
                        .map(Object::toString).sorted().collect(Collectors.joining(", "));
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", msg));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("mensaje", "El archivo supera 10 MB"));
    }
}
