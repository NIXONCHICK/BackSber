package back.dto;

import back.entities.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterRequest {
  @Email(message = "Email should be valid")
  @NotEmpty(message = "Email must not be empty")
  private String email;

  @NotEmpty(message = "Password must not be empty")
  private String password;

  @NotNull(message = "Role must not be empty")
  private Role role;
}
