package br.pucrs.pulsepass.iam;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IamErrors {
    @ExceptionHandler(IamException.class) ResponseEntity<?> known(IamException error) {
        return ResponseEntity.status(error.status()).body(Map.of("message", error.getMessage()));
    }
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception error) {
        return ResponseEntity.status(503).body(Map.of("message", "Serviço de sessão temporariamente indisponível. Tente novamente."));
    }
}
