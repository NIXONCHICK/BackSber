package back.controllers;

import back.dto.LoginRequest;
import back.dto.LoginResponse;
import back.services.AuthService;
import back.services.UserParsingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class LoginController {

  private final AuthService authService;
  private final UserParsingService userParsingService;

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody @Valid LoginRequest loginRequest, BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
      StringBuilder errorMsg = new StringBuilder();
      bindingResult.getFieldErrors().forEach(fieldError ->
          errorMsg.append(fieldError.getDefaultMessage()).append("; "));
      return ResponseEntity.badRequest().body("{\"message\": \"" + errorMsg + "\"}");
    }

    try {
      LoginResponse loginResponse = authService.login(loginRequest);
      userParsingService.parseAndUpdateUser(loginRequest, loginResponse.getId());
      return ResponseEntity.ok(loginResponse);
    } catch (IllegalStateException ex) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body("{\"message\": \"" + ex.getMessage() + "\"}");
    }
  }
}
