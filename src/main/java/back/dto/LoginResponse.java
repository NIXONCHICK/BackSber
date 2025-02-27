package back.dto;

import back.entities.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
  private long id;
  private String email;
  private Role role;
}
