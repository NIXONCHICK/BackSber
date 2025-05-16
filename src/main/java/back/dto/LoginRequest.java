package back.dto;

import jakarta.validation.constraints.*;
// import jakarta.validation.constraints.Size; // Убираем импорт Size, если он больше не нужен (NotBlank его заменяет для проверки на пустоту)
import lombok.Data;

@Data
public class LoginRequest {
  @Email(message = "Email should be valid")
  @NotEmpty(message = "Email must not be empty")
  private String email;

  @NotEmpty(message = "Password must not be empty")
  private String password;
}
