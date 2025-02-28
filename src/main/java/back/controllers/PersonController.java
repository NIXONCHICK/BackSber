package back.controllers;

import back.dto.LoginRequest;
import back.dto.LoginResponse;
import back.dto.RegisterRequest;
import back.services.AuthService;
import back.services.UserParsingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class PersonController {

  private final AuthService authService;
  private final UserParsingService userParsingService;

  @PostMapping("/auth/register")
  public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest registerRequest, BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
      StringBuilder errorMsg = new StringBuilder();
      bindingResult.getFieldErrors().forEach(fieldError ->
          errorMsg.append(fieldError.getDefaultMessage()).append("; "));
      return ResponseEntity.badRequest().body("{\"message\": \"" + errorMsg + "\"}");
    }

    try {
      LoginResponse loginResponse = authService.register(registerRequest);
      userParsingService.parseAndUpdateUser(registerRequest, loginResponse.getId());
      return ResponseEntity.status(HttpStatus.CREATED).body(loginResponse);
    } catch (IllegalStateException ex) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body("{\"message\": \"" + ex.getMessage() + "\"}");
    } catch (Exception ex) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("{\"message\": \"" + ex.getMessage() + "\"}");
    }
  }
  
  @PostMapping("/auth/login")
  public ResponseEntity<?> login(@RequestBody @Valid LoginRequest loginRequest, BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
      StringBuilder errorMsg = new StringBuilder();
      bindingResult.getFieldErrors().forEach(fieldError ->
          errorMsg.append(fieldError.getDefaultMessage()).append("; "));
      return ResponseEntity.badRequest().body("{\"message\": \"" + errorMsg + "\"}");
    }

    try {
      LoginResponse loginResponse = authService.login(loginRequest);
      return ResponseEntity.ok(loginResponse);
    } catch (BadCredentialsException ex) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body("{\"message\": \"Неверный email или пароль\"}");
    } catch (Exception ex) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("{\"message\": \"" + ex.getMessage() + "\"}");
    }
  }



}