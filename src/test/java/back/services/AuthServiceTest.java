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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private PersonRepository personRepository;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private EncryptionUtil encryptionUtil;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserParsingService userParsingService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;
    private Person validPerson;
    
    @BeforeEach
    void setUp() {
        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setEmail("test.student@sfedu.ru");
        validRegisterRequest.setPassword("validPassword123");
        validRegisterRequest.setRole(Role.STUDENT);

        validLoginRequest = new LoginRequest();
        validLoginRequest.setEmail("test.student@sfedu.ru");
        validLoginRequest.setPassword("validPassword123");

        validPerson = new Person();
        validPerson.setId(1L);
        validPerson.setEmail("test.student@sfedu.ru");
        validPerson.setPassword("encryptedPassword");
        validPerson.setRole(Role.STUDENT);
        validPerson.setMoodleSession("test_moodle_session");
    }

    @Test
    @DisplayName("Успешная регистрация нового пользователя")
    void register_ValidUser_Success() {
        when(personRepository.findByEmail(validRegisterRequest.getEmail())).thenReturn(null);
        when(userParsingService.validateSfedUCredentialsAndGetSession(
                validRegisterRequest.getEmail(), 
                validRegisterRequest.getPassword()))
                .thenReturn("test_moodle_session");
        when(modelMapper.map(validRegisterRequest, Person.class)).thenReturn(validPerson);
        when(encryptionUtil.encryptPassword(validRegisterRequest.getPassword())).thenReturn("encryptedPassword");
        when(personRepository.save(any(Person.class))).thenReturn(validPerson);
        when(jwtUtil.generateToken(any(Map.class), any(Person.class))).thenReturn("jwt_token");

        LoginResponse result = authService.register(validRegisterRequest);

        assertNotNull(result);
        assertEquals(validPerson.getId(), result.getId());
        assertEquals(validPerson.getEmail(), result.getEmail());
        assertEquals(Role.STUDENT, result.getRole());
        assertEquals("jwt_token", result.getToken());

        verify(personRepository).findByEmail(validRegisterRequest.getEmail());
        verify(userParsingService).validateSfedUCredentialsAndGetSession(
                validRegisterRequest.getEmail(), 
                validRegisterRequest.getPassword());
        verify(encryptionUtil).encryptPassword(validRegisterRequest.getPassword());
        verify(personRepository).save(any(Person.class));
        verify(jwtUtil).generateToken(any(Map.class), any(Person.class));
    }

    @Test
    @DisplayName("Регистрация - пользователь уже существует")
    void register_UserAlreadyExists_ThrowsIllegalStateException() {
        when(personRepository.findByEmail(validRegisterRequest.getEmail())).thenReturn(validPerson);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> authService.register(validRegisterRequest)
        );
        
        assertEquals("Пользователь с таким email уже существует.", exception.getMessage());
        
        verify(personRepository).findByEmail(validRegisterRequest.getEmail());
        verify(userParsingService, never()).validateSfedUCredentialsAndGetSession(anyString(), anyString());
        verify(personRepository, never()).save(any(Person.class));
    }

    @Test
    @DisplayName("Регистрация - неверный email SFEDU")
    void register_InvalidSfedUEmail_ThrowsSfedAuthenticationException() {
        when(personRepository.findByEmail(validRegisterRequest.getEmail())).thenReturn(null);
        when(userParsingService.validateSfedUCredentialsAndGetSession(
                validRegisterRequest.getEmail(), 
                validRegisterRequest.getPassword()))
                .thenThrow(new SfedAuthenticationException("INVALID_SFEDU_EMAIL", "Неверный email SFEDU."));

        SfedAuthenticationException exception = assertThrows(
                SfedAuthenticationException.class,
                () -> authService.register(validRegisterRequest)
        );
        
        assertEquals("INVALID_SFEDU_EMAIL", exception.getErrorCode());
        assertEquals("Неверный email SFEDU.", exception.getMessage());
        
        verify(personRepository).findByEmail(validRegisterRequest.getEmail());
        verify(userParsingService).validateSfedUCredentialsAndGetSession(
                validRegisterRequest.getEmail(), 
                validRegisterRequest.getPassword());
        verify(personRepository, never()).save(any(Person.class));
    }

    @Test
    @DisplayName("Регистрация - неверный пароль SFEDU")
    void register_InvalidSfedUPassword_ThrowsSfedAuthenticationException() {
        when(personRepository.findByEmail(validRegisterRequest.getEmail())).thenReturn(null);
        when(userParsingService.validateSfedUCredentialsAndGetSession(
                validRegisterRequest.getEmail(), 
                validRegisterRequest.getPassword()))
                .thenThrow(new SfedAuthenticationException("INVALID_SFEDU_PASSWORD", "Неверный пароль SFEDU."));

        SfedAuthenticationException exception = assertThrows(
                SfedAuthenticationException.class,
                () -> authService.register(validRegisterRequest)
        );
        
        assertEquals("INVALID_SFEDU_PASSWORD", exception.getErrorCode());
        assertEquals("Неверный пароль SFEDU.", exception.getMessage());
    }

    @Test
    @DisplayName("Регистрация - тайм-аут подключения к SFEDU")
    void register_SfedUTimeout_ThrowsSfedAuthenticationException() {
        when(personRepository.findByEmail(validRegisterRequest.getEmail())).thenReturn(null);
        when(userParsingService.validateSfedUCredentialsAndGetSession(
                validRegisterRequest.getEmail(), 
                validRegisterRequest.getPassword()))
                .thenThrow(new SfedAuthenticationException("SFEDU_LOGIN_TIMEOUT", "Тайм-аут при входе в SFEDU."));

        SfedAuthenticationException exception = assertThrows(
                SfedAuthenticationException.class,
                () -> authService.register(validRegisterRequest)
        );
        
        assertEquals("SFEDU_LOGIN_TIMEOUT", exception.getErrorCode());
        assertEquals("Тайм-аут при входе в SFEDU.", exception.getMessage());
    }

    @Test
    @DisplayName("Успешный логин существующего пользователя")
    void login_ValidCredentials_Success() {
        Authentication mockAuth = mock(Authentication.class);
        when(mockAuth.getPrincipal()).thenReturn(validPerson);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mockAuth);
        when(personRepository.findByEmail(validPerson.getEmail())).thenReturn(validPerson);
        when(jwtUtil.generateToken(any(Map.class), any(Person.class))).thenReturn("jwt_token");

        LoginResponse result = authService.login(validLoginRequest);

        assertNotNull(result);
        assertEquals(validPerson.getId(), result.getId());
        assertEquals(validPerson.getEmail(), result.getEmail());
        assertEquals(validPerson.getRole(), result.getRole());
        assertEquals("jwt_token", result.getToken());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(personRepository).findByEmail(validPerson.getEmail());
        verify(jwtUtil).generateToken(any(Map.class), eq(validPerson));
    }

    @Test
    @DisplayName("Логин - неверные учетные данные")
    void login_InvalidCredentials_ThrowsBadCredentialsException() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(validLoginRequest)
        );
        
        assertEquals("Bad credentials", exception.getMessage());
        
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(personRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("Логин - пользователь не найден в базе данных")
    void login_UserNotFoundInDatabase_ThrowsUsernameNotFoundException() {
        Authentication mockAuth = mock(Authentication.class);
        when(mockAuth.getPrincipal()).thenReturn(validPerson);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mockAuth);
        when(personRepository.findByEmail(validPerson.getEmail())).thenReturn(null);

        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> authService.login(validLoginRequest)
        );
        
        assertEquals("Пользователь с email: " + validLoginRequest.getEmail() + " не найден", 
                exception.getMessage());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(personRepository).findByEmail(validPerson.getEmail());
        verify(jwtUtil, never()).generateToken(any(Map.class), any(Person.class));
    }

    @Test
    @DisplayName("Регистрация - проверка корректной установки роли STUDENT")
    void register_ValidUser_SetsStudentRole() {
        when(personRepository.findByEmail(validRegisterRequest.getEmail())).thenReturn(null);
        when(userParsingService.validateSfedUCredentialsAndGetSession(
                validRegisterRequest.getEmail(), 
                validRegisterRequest.getPassword()))
                .thenReturn("test_moodle_session");
        when(modelMapper.map(validRegisterRequest, Person.class)).thenReturn(validPerson);
        when(encryptionUtil.encryptPassword(validRegisterRequest.getPassword())).thenReturn("encryptedPassword");
        when(jwtUtil.generateToken(any(Map.class), any(Person.class))).thenReturn("jwt_token");

        authService.register(validRegisterRequest);

        verify(personRepository).save(argThat(person -> 
                person.getRole() == Role.STUDENT && 
                person.getMoodleSession().equals("test_moodle_session") &&
                person.getPassword().equals("encryptedPassword")
        ));
    }

    @Test
    @DisplayName("Логин - проверка корректного формирования JWT claims")
    void login_ValidCredentials_GeneratesCorrectJwtClaims() {
        Authentication mockAuth = mock(Authentication.class);
        when(mockAuth.getPrincipal()).thenReturn(validPerson);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mockAuth);
        when(personRepository.findByEmail(validPerson.getEmail())).thenReturn(validPerson);
        when(jwtUtil.generateToken(any(Map.class), any(Person.class))).thenReturn("jwt_token");

        authService.login(validLoginRequest);

        verify(jwtUtil).generateToken(argThat(claims -> 
                claims.get("role").equals("STUDENT") && 
                claims.get("userId").equals(1L)
        ), eq(validPerson));
    }

    @Test
    @DisplayName("Регистрация - проверка корректного формирования JWT claims")
    void register_ValidUser_GeneratesCorrectJwtClaims() {
        when(personRepository.findByEmail(validRegisterRequest.getEmail())).thenReturn(null);
        when(userParsingService.validateSfedUCredentialsAndGetSession(
                validRegisterRequest.getEmail(), 
                validRegisterRequest.getPassword()))
                .thenReturn("test_moodle_session");
        when(modelMapper.map(validRegisterRequest, Person.class)).thenReturn(validPerson);
        when(encryptionUtil.encryptPassword(validRegisterRequest.getPassword())).thenReturn("encryptedPassword");
        when(jwtUtil.generateToken(any(Map.class), any(Person.class))).thenReturn("jwt_token");

        authService.register(validRegisterRequest);

        verify(jwtUtil).generateToken(argThat(claims -> 
                claims.get("role").equals("STUDENT") && 
                claims.get("userId").equals(1L)
        ), any(Person.class));
    }
} 