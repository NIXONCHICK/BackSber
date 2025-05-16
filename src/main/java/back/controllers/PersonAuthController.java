package back.controllers;

import back.dto.LoginRequest;
import back.dto.LoginResponse;
import back.dto.RegisterRequest;
import back.exceptions.SfedAuthenticationException;
import back.services.AuthService;
import back.services.UserParsingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import back.entities.Person;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class PersonAuthController {

  private final AuthService authService;
  private final UserParsingService userParsingService;

  @PostMapping("/auth/register")
  public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest registerRequest, BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
      StringBuilder errorMsg = new StringBuilder();
      bindingResult.getFieldErrors().forEach(fieldError ->
          errorMsg.append(fieldError.getDefaultMessage()).append("; "));
      return ResponseEntity.badRequest().body(Map.of("message", errorMsg.toString().trim()));
    }

    try {
      LoginResponse loginResponse = authService.register(registerRequest);
      return ResponseEntity.status(HttpStatus.CREATED).body(loginResponse);
    } catch (SfedAuthenticationException ex) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("message", ex.getMessage(), "errorCode", ex.getErrorCode()));
    } catch (IllegalStateException ex) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("message", ex.getMessage()));
    } catch (Exception ex) {
      ex.printStackTrace();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("message", "Внутренняя ошибка сервера при регистрации."));
    }
  }
  
  @PostMapping("/user/initiate-parsing")
  public ResponseEntity<?> initiateParsing(@AuthenticationPrincipal Person authenticatedPerson) {
    if (authenticatedPerson == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Пользователь не аутентифицирован."));
    }
    try {
      long userId = authenticatedPerson.getId();
      boolean parsingResult = userParsingService.parseAndUpdateUser(userId);
      if (parsingResult) {
        return ResponseEntity.ok(Map.of("message", "Сбор данных успешно завершен."));
      } else {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Ошибка при сборе данных."));
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("message", "Внутренняя ошибка сервера при инициации сбора данных."));
    }
  }

  @PostMapping("/auth/login")
  public ResponseEntity<?> login(@RequestBody @Valid LoginRequest loginRequest, BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
      StringBuilder errorMsg = new StringBuilder();
      bindingResult.getFieldErrors().forEach(fieldError ->
          errorMsg.append(fieldError.getDefaultMessage()).append("; "));
      return ResponseEntity.badRequest().body(Map.of("message", errorMsg.toString().trim()));
    }

    try {
      LoginResponse loginResponse = authService.login(loginRequest);
      return ResponseEntity.ok(loginResponse);
    } catch (BadCredentialsException ex) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("message", "Неверный email или пароль"));
    } catch (Exception ex) {
      ex.printStackTrace();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("message", "Внутренняя ошибка сервера при входе: " + ex.getMessage()));
    }
  }
}