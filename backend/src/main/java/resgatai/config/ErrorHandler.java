package resgatai.config;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Todas as respostas de erro seguem o formato Problem Details (RFC 9457).
 * Erros de validação listam cada campo inválido em "erros" (UC01 fluxo 6a, UC02 fluxo 9a).
 */
@RestControllerAdvice
public class ErrorHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> erros = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> erros.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ex.getBody().setProperty("erros", erros);
        return handleExceptionInternal(ex, ex.getBody(), headers, status, request);
    }

    /** Timeout esperando o lock de um lote disputado (UC04): a instituição pode tentar de novo. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ProblemDetail lockOcupado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "O lote está sendo reservado por outra instituição, tente novamente");
    }
}
