package back.services;

import back.dto.LoginRequest;
import back.dto.LoginResponse;

public interface AuthService {
  LoginResponse login(LoginRequest loginRequest);
}
