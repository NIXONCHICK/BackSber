package back.services;

import back.dto.LoginRequest;
import back.dto.LoginResponse;
import back.dto.RegisterRequest;
import back.entities.Person;
import back.repositories.PersonRepository;
import back.util.EncryptionUtil;
import back.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

  private final PersonRepository personRepository;
  private final ModelMapper modelMapper;
  private final JwtUtil jwtUtil;
  private final EncryptionUtil encryptionUtil;
  private final AuthenticationManager authenticationManager;

  @Transactional
  public LoginResponse register(RegisterRequest registerRequest) {
    Person existingPerson = personRepository.findByEmail(registerRequest.getEmail());
    if (existingPerson != null) {
      throw new IllegalStateException("Пользователь с таким email уже существует.");
    }
    
    Person person = modelMapper.map(registerRequest, Person.class);
    
    person.setPassword(encryptionUtil.encryptPassword(registerRequest.getPassword()));
    
    personRepository.save(person);
    
    Map<String, Object> claims = new HashMap<>();
    claims.put("role", person.getRole().name());
    String token = jwtUtil.generateToken(claims, person);
    
    return new LoginResponse(person.getId(), person.getEmail(), person.getRole(), token);
  }

  @Transactional
  public LoginResponse login(LoginRequest loginRequest) {
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
    );
    
    UserDetails userDetails = (UserDetails) authentication.getPrincipal();
    Person person = personRepository.findByEmail(userDetails.getUsername());
    
    if (person == null) {
      throw new UsernameNotFoundException("Пользователь с email: " + loginRequest.getEmail() + " не найден");
    }
    
    Map<String, Object> claims = new HashMap<>();
    claims.put("role", person.getRole().name());
    String token = jwtUtil.generateToken(claims, person);
    
    return new LoginResponse(person.getId(), person.getEmail(), person.getRole(), token);
  }
  
  public boolean validateToken(String token, UserDetails userDetails) {
    return jwtUtil.validateToken(token, userDetails);
  }
  
  public String extractUsername(String token) {
    return jwtUtil.extractUsername(token);
  }
}