package back.dto;

import back.entities.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
  @Email(message = "Email should be valid")
  @NotEmpty(message = "Email must not be empty")
  private String email;

  @Size(min = 6, message = "Password must be at least 6 characters long")
  @NotEmpty(message = "Password must not be empty")
  private String password;

  @NotNull(message = "Role must not be empty")
  private Role role;
}
