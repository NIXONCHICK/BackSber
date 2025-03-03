package back.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(DuplicateTaskException.class)
    public ResponseEntity<Object> handleDuplicateTaskException(DuplicateTaskException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "duplicate_task");
        response.put("message", ex.getMessage());
        response.put("details", "Вы уже создали похожее задание. Пожалуйста, проверьте свой список заданий.");
        
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex) {
        if (ex.getMessage().contains("Можно изменять только задания")) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "forbidden_operation");
            response.put("message", ex.getMessage());
            
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "server_error");
        response.put("message", ex.getMessage());
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}