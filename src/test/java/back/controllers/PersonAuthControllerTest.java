package back.controllers;

import back.dto.LoginRequest;
import back.dto.LoginResponse;
import back.dto.RegisterRequest;
import back.entities.Role;
import back.exceptions.SfedAuthenticationException;
import back.services.AuthService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(PersonAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ImportAutoConfiguration({ValidationAutoConfiguration.class})
@DisplayName("PersonAuthController Integration Tests")
class PersonAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;



    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;
    private LoginResponse loginResponse;

    @BeforeEach
    void setUp() {
        validRegisterRequest = new RegisterRequest();
        validRegisterRequest.setEmail("test.student@sfedu.ru");
        validRegisterRequest.setPassword("validPassword123");
        validRegisterRequest.setRole(Role.STUDENT);

        validLoginRequest = new LoginRequest();
        validLoginRequest.setEmail("test.student@sfedu.ru");
        validLoginRequest.setPassword("validPassword123");

        loginResponse = new LoginResponse();
        loginResponse.setId(1L);
        loginResponse.setEmail("test.student@sfedu.ru");
        loginResponse.setRole(Role.STUDENT);
        loginResponse.setToken("jwt_token_123");
    }

    @Test
    @DisplayName("POST /api/auth/register - успешная регистрация")
    void register_ValidRequest_ReturnsCreated() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(loginResponse);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("test.student@sfedu.ru"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.token").value("jwt_token_123"));
    }

    @Test
    @DisplayName("POST /api/auth/register - валидация пустого email")
    void register_EmptyEmail_ReturnsBadRequest() throws Exception {
        validRegisterRequest.setEmail("");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register - валидация некорректного email")
    void register_InvalidEmail_ReturnsBadRequest() throws Exception {
        validRegisterRequest.setEmail("invalid-email");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register - валидация пустого пароля")
    void register_EmptyPassword_ReturnsBadRequest() throws Exception {
        validRegisterRequest.setPassword("");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register - пользователь уже существует")
    void register_UserAlreadyExists_ReturnsConflict() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new IllegalStateException("Пользователь с таким email уже существует."));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Пользователь с таким email уже существует."));
    }

    @Test
    @DisplayName("POST /api/auth/register - неверные учетные данные SFEDU")
    void register_InvalidSfedUCredentials_ReturnsUnauthorized() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new SfedAuthenticationException("INVALID_SFEDU_EMAIL", "Неверный email SFEDU."));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Неверный email SFEDU."))
                .andExpect(jsonPath("$.errorCode").value("INVALID_SFEDU_EMAIL"));
    }

    @Test
    @DisplayName("POST /api/auth/register - внутренняя ошибка сервера")
    void register_InternalServerError_ReturnsInternalServerError() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Внутренняя ошибка сервера при регистрации."));
    }

    @Test
    @DisplayName("POST /api/auth/login - успешный логин")
    void login_ValidCredentials_ReturnsOk() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("test.student@sfedu.ru"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.token").value("jwt_token_123"));
    }

    @Test
    @DisplayName("POST /api/auth/login - валидация пустого email")
    void login_EmptyEmail_ReturnsBadRequest() throws Exception {
        validLoginRequest.setEmail("");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login - валидация некорректного email")
    void login_InvalidEmail_ReturnsBadRequest() throws Exception {
        validLoginRequest.setEmail("invalid-email");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login - валидация пустого пароля")
    void login_EmptyPassword_ReturnsBadRequest() throws Exception {
        validLoginRequest.setPassword("");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login - неверные учетные данные")
    void login_InvalidCredentials_ReturnsUnauthorized() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Неверные учетные данные"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Неверный email или пароль"));
    }

    @Test
    @DisplayName("POST /api/auth/login - внутренняя ошибка сервера")
    void login_InternalServerError_ReturnsInternalServerError() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Внутренняя ошибка сервера при входе: Database connection failed"));
    }

    @Test
    @DisplayName("POST /api/auth/register - неподдерживаемый Content-Type")
    void register_UnsupportedMediaType_ReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.TEXT_PLAIN)
                .content("plain text"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("POST /api/auth/login - неподдерживаемый Content-Type")
    void login_UnsupportedMediaType_ReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.TEXT_PLAIN)
                .content("plain text"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("POST /api/auth/register - некорректная структура JSON")
    void register_MalformedJson_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invalid\": \"json\""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login - некорректная структура JSON")
    void login_MalformedJson_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"invalid\": \"json\""))
                .andExpect(status().isBadRequest());
    }
} 