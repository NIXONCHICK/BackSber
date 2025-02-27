package back.dto;

import back.entities.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LoginRequest {
  @Email(message = "Email should be valid")
  @NotEmpty(message = "Email must not be empty")
  private String email;

  @Size(min = 6, message = "Password must be at least 6 characters long")
  @NotEmpty(message = "Password must not be empty")
  private String password;

  @NotNull(message = "Role must not be empty")
  private Role role;
}
