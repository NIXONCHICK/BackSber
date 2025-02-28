package back.services;

import back.dto.LoginRequest;
import back.dto.LoginResponse;
import back.dto.RegisterRequest;
import org.springframework.security.core.userdetails.UserDetails;

public interface AuthService {

  LoginResponse register(RegisterRequest registerRequest);
  

  LoginResponse login(LoginRequest loginRequest);
  

  boolean validateToken(String token, UserDetails userDetails);
  

  String extractUsername(String token);
}