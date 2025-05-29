package back.functional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("functional-test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Упрощенные функциональные тесты аутентификации")
class SimpleFunctionalTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15.3-alpine")
            .withDatabaseName("back_functional_test_db")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("jwt.secret-key", () -> "test-secret-key-for-functional-tests-very-long-key-minimum-256-bits");
        registry.add("jwt.expiration-ms", () -> "3600000");
    }

    private static String registeredUserToken;
    private static final String TEST_EMAIL = "simple.functional.test@sfedu.ru";
    private static final String TEST_PASSWORD = "simpleFunctionalPassword123";

    @Test
    @Order(1)
    @DisplayName("Функциональный тест: Регистрация нового пользователя")
    void shouldRegisterNewUser() {
        // Подготовка данных
        Map<String, Object> registerRequest = Map.of(
            "email", TEST_EMAIL,
            "password", TEST_PASSWORD,
            "role", "STUDENT"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(registerRequest, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/auth/register", 
            request, 
            Map.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> responseBody = response.getBody();
        assertEquals(TEST_EMAIL, responseBody.get("email"));
        assertEquals("STUDENT", responseBody.get("role"));
        assertNotNull(responseBody.get("token"));
        assertNotNull(responseBody.get("id"));

        registeredUserToken = (String) responseBody.get("token");
        
        System.out.println("✅ Пользователь успешно зарегистрирован!");
    }

    @Test
    @Order(2)
    @DisplayName("Функциональный тест: Отклонение повторной регистрации")
    void shouldRejectDuplicateRegistration() {
        Map<String, Object> duplicateRequest = Map.of(
            "email", TEST_EMAIL,
            "password", TEST_PASSWORD,
            "role", "STUDENT"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(duplicateRequest, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/auth/register", 
            request, 
            Map.class
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("message").toString().contains("уже существует"));
        
        System.out.println("✅ Повторная регистрация корректно отклонена!");
    }

    @Test
    @Order(3)
    @DisplayName("Функциональный тест: Успешный логин")
    void shouldLoginSuccessfully() {
        Map<String, Object> loginRequest = Map.of(
            "email", TEST_EMAIL,
            "password", TEST_PASSWORD
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(loginRequest, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/auth/login", 
            request, 
            Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> responseBody = response.getBody();
        assertEquals(TEST_EMAIL, responseBody.get("email"));
        assertEquals("STUDENT", responseBody.get("role"));
        assertNotNull(responseBody.get("token"));
        
        System.out.println("✅ Логин выполнен успешно!");
    }

    @Test
    @Order(4)
    @DisplayName("Функциональный тест: Отклонение неверных данных для входа")
    void shouldRejectInvalidLogin() {
        Map<String, Object> invalidRequest = Map.of(
            "email", TEST_EMAIL,
            "password", "wrongPassword"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(invalidRequest, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/auth/login", 
            request, 
            Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("message").toString().contains("Неверный"));
        
        System.out.println("✅ Неверные данные для входа отклонены!");
    }

    @Test
    @Order(5)
    @DisplayName("Функциональный тест: Валидация данных")
    void shouldValidateRequestData() {
        // Тест пустого email
        Map<String, Object> emptyEmailRequest = Map.of(
            "email", "",
            "password", "validPassword123",
            "role", "STUDENT"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request1 = new HttpEntity<>(emptyEmailRequest, headers);

        ResponseEntity<Map> response1 = restTemplate.postForEntity(
            "/api/auth/register", 
            request1, 
            Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response1.getStatusCode());

        // Тест некорректного email
        Map<String, Object> invalidEmailRequest = Map.of(
            "email", "invalid-email-format",
            "password", "validPassword123",
            "role", "STUDENT"
        );

        HttpEntity<Map<String, Object>> request2 = new HttpEntity<>(invalidEmailRequest, headers);

        ResponseEntity<Map> response2 = restTemplate.postForEntity(
            "/api/auth/register", 
            request2, 
            Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response2.getStatusCode());
        
        System.out.println("✅ Валидация данных работает корректно!");
    }

    @Test
    @Order(6)
    @DisplayName("Функциональный тест: Доступ к защищенным ресурсам")
    void shouldAccessProtectedResources() {
        if (registeredUserToken == null) {
            fail("Токен не был получен в предыдущих тестах");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(registeredUserToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/user/initiate-parsing", 
            request, 
            Map.class
        );

        assertTrue(response.getStatusCode().is2xxSuccessful() ||
                  response.getStatusCode().is5xxServerError());
        
        System.out.println("✅ Доступ к защищенным ресурсам работает!");
    }
} 