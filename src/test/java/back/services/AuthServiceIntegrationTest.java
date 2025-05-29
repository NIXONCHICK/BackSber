package back.services;

import back.dto.LoginRequest;
import back.dto.LoginResponse;
import back.dto.RegisterRequest;
import back.entities.Person;
import back.entities.Role;
import back.exceptions.SfedAuthenticationException;
import back.repositories.PersonRepository;
import back.util.EncryptionUtil;
import back.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("AuthService Integration Tests")
class AuthServiceIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private PersonRepository personRepository;

    @MockitoBean
    private UserParsingService userParsingService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private EncryptionUtil encryptionUtil;

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;

    @BeforeEach
    void setUp() {
        personRepository.deleteAll();

        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setEmail("test.student@sfedu.ru");
        validRegisterRequest.setPassword("validPassword123");
        validRegisterRequest.setRole(Role.STUDENT);

        validLoginRequest = new LoginRequest();
        validLoginRequest.setEmail("test.student@sfedu.ru");
        validLoginRequest.setPassword("validPassword123");
    }

    @Test
    @DisplayName("Интеграционный тест: успешная регистрация нового пользователя")
    void register_NewUser_Success() throws Exception {
        when(userParsingService.validateSfedUCredentialsAndGetSession(
                validRegisterRequest.getEmail(), 
                validRegisterRequest.getPassword()))
                .thenReturn("test_moodle_session");
        
        when(encryptionUtil.encryptPassword(validRegisterRequest.getPassword()))
                .thenReturn("encrypted_password_123");
        
        when(jwtUtil.generateToken(any(), any(Person.class)))
                .thenReturn("jwt_token_123");

        LoginResponse response = authService.register(validRegisterRequest);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo(validRegisterRequest.getEmail());
        assertThat(response.getRole()).isEqualTo(Role.STUDENT);
        assertThat(response.getToken()).isEqualTo("jwt_token_123");

        Person savedPerson = personRepository.findByEmail(validRegisterRequest.getEmail());
        assertThat(savedPerson).isNotNull();
        assertThat(savedPerson.getEmail()).isEqualTo(validRegisterRequest.getEmail());
        assertThat(savedPerson.getRole()).isEqualTo(Role.STUDENT);
        assertThat(savedPerson.getPassword()).isEqualTo("encrypted_password_123");
        assertThat(savedPerson.getMoodleSession()).isEqualTo("test_moodle_session");

        verify(userParsingService).validateSfedUCredentialsAndGetSession(
                validRegisterRequest.getEmail(), 
                validRegisterRequest.getPassword());
        verify(encryptionUtil).encryptPassword(validRegisterRequest.getPassword());
        verify(jwtUtil).generateToken(any(), any(Person.class));
    }

    @Test
    @DisplayName("Интеграционный тест: регистрация с существующим email")
    void register_ExistingUser_ThrowsException() throws Exception {
        Person existingPerson = new Person();
        existingPerson.setEmail(validRegisterRequest.getEmail());
        existingPerson.setPassword("old_password");
        existingPerson.setRole(Role.STUDENT);
        existingPerson.setAccountNonExpired(true);
        existingPerson.setAccountNonLocked(true);
        existingPerson.setCredentialsNonExpired(true);
        existingPerson.setEnabled(true);
        personRepository.save(existingPerson);

        assertThatThrownBy(() -> authService.register(validRegisterRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Пользователь с таким email уже существует.");

        verify(userParsingService, never()).validateSfedUCredentialsAndGetSession(anyString(), anyString());
        verify(encryptionUtil, never()).encryptPassword(anyString());
        verify(jwtUtil, never()).generateToken(any(), any(Person.class));
    }

    @Test
    @DisplayName("Интеграционный тест: регистрация с неверными SFEDU данными")
    void register_InvalidSfedUCredentials_ThrowsException() throws Exception {
        when(userParsingService.validateSfedUCredentialsAndGetSession(
                validRegisterRequest.getEmail(), 
                validRegisterRequest.getPassword()))
                .thenThrow(new SfedAuthenticationException("INVALID_SFEDU_EMAIL", "Неверный email SFEDU."));

        assertThatThrownBy(() -> authService.register(validRegisterRequest))
                .isInstanceOf(SfedAuthenticationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_SFEDU_EMAIL")
                .hasMessage("Неверный email SFEDU.");

        Person savedPerson = personRepository.findByEmail(validRegisterRequest.getEmail());
        assertThat(savedPerson).isNull();

        verify(userParsingService).validateSfedUCredentialsAndGetSession(
                validRegisterRequest.getEmail(), 
                validRegisterRequest.getPassword());
        verify(encryptionUtil, never()).encryptPassword(anyString());
        verify(jwtUtil, never()).generateToken(any(), any(Person.class));
    }

    @Test
    @DisplayName("Интеграционный тест: успешный логин")
    void login_ValidCredentials_Success() throws Exception {
        Person existingPerson = new Person();
        existingPerson.setEmail(validLoginRequest.getEmail());
        existingPerson.setPassword("encrypted_password");
        existingPerson.setRole(Role.STUDENT);
        existingPerson.setAccountNonExpired(true);
        existingPerson.setAccountNonLocked(true);
        existingPerson.setCredentialsNonExpired(true);
        existingPerson.setEnabled(true);
        existingPerson = personRepository.save(existingPerson);

        Authentication mockAuth = mock(Authentication.class);
        when(mockAuth.getPrincipal()).thenReturn(existingPerson);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mockAuth);
        when(jwtUtil.generateToken(any(), any(Person.class)))
                .thenReturn("jwt_token_456");

        LoginResponse response = authService.login(validLoginRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(existingPerson.getId());
        assertThat(response.getEmail()).isEqualTo(existingPerson.getEmail());
        assertThat(response.getRole()).isEqualTo(Role.STUDENT);
        assertThat(response.getToken()).isEqualTo("jwt_token_456");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtUtil).generateToken(any(), any(Person.class));
    }

    @Test
    @DisplayName("Интеграционный тест: логин с неверными данными")
    void login_InvalidCredentials_ThrowsException() throws Exception {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Неверные учетные данные"));

        assertThatThrownBy(() -> authService.login(validLoginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Неверные учетные данные");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtUtil, never()).generateToken(any(), any(Person.class));
    }

    @Test
    @DisplayName("Интеграционный тест: полный цикл регистрации и логина")
    void fullCycle_RegisterThenLogin_Success() throws Exception {
        when(userParsingService.validateSfedUCredentialsAndGetSession(
                validRegisterRequest.getEmail(), 
                validRegisterRequest.getPassword()))
                .thenReturn("test_moodle_session");
        when(encryptionUtil.encryptPassword(validRegisterRequest.getPassword()))
                .thenReturn("encrypted_password_123");
        when(jwtUtil.generateToken(any(), any(Person.class)))
                .thenReturn("registration_token");

        LoginResponse registerResponse = authService.register(validRegisterRequest);
        assertThat(registerResponse).isNotNull();
        assertThat(registerResponse.getToken()).isEqualTo("registration_token");

        Person registeredPerson = personRepository.findByEmail(validRegisterRequest.getEmail());
        Authentication mockAuth = mock(Authentication.class);
        when(mockAuth.getPrincipal()).thenReturn(registeredPerson);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mockAuth);
        when(jwtUtil.generateToken(any(), any(Person.class)))
                .thenReturn("login_token");

        LoginResponse loginResponse = authService.login(validLoginRequest);

        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.getId()).isEqualTo(registeredPerson.getId());
        assertThat(loginResponse.getEmail()).isEqualTo(validLoginRequest.getEmail());
        assertThat(loginResponse.getRole()).isEqualTo(Role.STUDENT);
        assertThat(loginResponse.getToken()).isEqualTo("login_token");

        long userCount = personRepository.count();
        assertThat(userCount).isEqualTo(1);
    }
} 