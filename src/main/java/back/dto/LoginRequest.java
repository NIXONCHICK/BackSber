package back.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LoginRequest {
  @Email(message = "Email should be valid")
  @NotEmpty(message = "Email must not be empty")
  private String email;

  @NotEmpty(message = "Password must not be empty")
  private String password;
}
