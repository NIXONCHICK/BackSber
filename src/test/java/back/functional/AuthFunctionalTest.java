package back.functional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.test.context.TestPropertySource;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
    "jwt.secret-key=test-secret-key-for-functional-tests-very-long-key-minimum-256-bits",
    "jwt.expiration-ms=3600000"
})
@DisplayName("Функциональные тесты аутентификации")
class AuthFunctionalTest extends BaseFunctionalTest {

    private static String registeredUserToken;
    private static final String TEST_EMAIL = "functional.test@sfedu.ru";
    private static final String TEST_PASSWORD = "functionalTestPassword123";

    @Test
    @Order(1)
    @DisplayName("Функциональный тест: Полный цикл регистрации пользователя")
    void shouldCompleteUserRegistrationFlow() {
        String registerRequestBody = """
            {
                "email": "%s",
                "password": "%s",
                "role": "STUDENT"
            }
            """.formatted(TEST_EMAIL, TEST_PASSWORD);

        var response = givenJsonRequest()
            .body(registerRequestBody)
        .when()
            .post("/auth/register")
        .then()
            .statusCode(201)
            .contentType("application/json")
            .body("email", equalTo(TEST_EMAIL))
            .body("role", equalTo("STUDENT"))
            .body("token", notNullValue())
            .body("id", notNullValue())
            .extract()
            .response();

        registeredUserToken = response.jsonPath().getString("token");
        
        System.out.println("✅ Пользователь успешно зарегистрирован с токеном: " + 
                          registeredUserToken.substring(0, 20) + "...");
    }

    @Test
    @Order(2)
    @DisplayName("Функциональный тест: Попытка повторной регистрации того же пользователя")
    void shouldRejectDuplicateRegistration() {
        String duplicateRegisterRequestBody = """
            {
                "email": "%s",
                "password": "%s",
                "role": "STUDENT"
            }
            """.formatted(TEST_EMAIL, TEST_PASSWORD);

        givenJsonRequest()
            .body(duplicateRegisterRequestBody)
        .when()
            .post("/auth/register")
        .then()
            .statusCode(409)
            .contentType("application/json")
            .body("message", containsString("уже существует"));
            
        System.out.println("✅ Повторная регистрация корректно отклонена");
    }

    @Test
    @Order(3)
    @DisplayName("Функциональный тест: Логин зарегистрированного пользователя")
    void shouldLoginRegisteredUser() {
        String loginRequestBody = """
            {
                "email": "%s",
                "password": "%s"
            }
            """.formatted(TEST_EMAIL, TEST_PASSWORD);

        givenJsonRequest()
            .body(loginRequestBody)
        .when()
            .post("/auth/login")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .body("email", equalTo(TEST_EMAIL))
            .body("role", equalTo("STUDENT"))
            .body("token", notNullValue())
            .body("id", notNullValue());
            
        System.out.println("✅ Пользователь успешно выполнил вход");
    }

    @Test
    @Order(4)
    @DisplayName("Функциональный тест: Попытка логина с неверными данными")
    void shouldRejectInvalidLogin() {
        String invalidLoginRequestBody = """
            {
                "email": "%s",
                "password": "wrongPassword"
            }
            """.formatted(TEST_EMAIL);

        givenJsonRequest()
            .body(invalidLoginRequestBody)
        .when()
            .post("/auth/login")
        .then()
            .statusCode(401)
            .contentType("application/json")
            .body("message", containsString("Неверный email или пароль"));
            
        System.out.println("✅ Неверные данные для входа корректно отклонены");
    }

    @Test
    @Order(5)
    @DisplayName("Функциональный тест: Валидация данных при регистрации")
    void shouldValidateRegistrationData() {
        String invalidEmailRequestBody = """
            {
                "email": "",
                "password": "validPassword123",
                "role": "STUDENT"
            }
            """;

        givenJsonRequest()
            .body(invalidEmailRequestBody)
        .when()
            .post("/auth/register")
        .then()
            .statusCode(400)
            .contentType("application/json")
            .body("message", notNullValue());

        String malformedEmailRequestBody = """
            {
                "email": "invalid-email-format",
                "password": "validPassword123",
                "role": "STUDENT"
            }
            """;

        givenJsonRequest()
            .body(malformedEmailRequestBody)
        .when()
            .post("/auth/register")
        .then()
            .statusCode(400)
            .contentType("application/json")
            .body("message", notNullValue());

        String emptyPasswordRequestBody = """
            {
                "email": "test.validation@sfedu.ru",
                "password": "",
                "role": "STUDENT"
            }
            """;

        givenJsonRequest()
            .body(emptyPasswordRequestBody)
        .when()
            .post("/auth/register")
        .then()
            .statusCode(400)
            .contentType("application/json")
            .body("message", notNullValue());
            
        System.out.println("✅ Валидация данных работает корректно");
    }

    @Test
    @Order(6)
    @DisplayName("Функциональный тест: Доступ к защищенным эндпоинтам с токеном")
    void shouldAccessProtectedEndpointsWithValidToken() {
        if (registeredUserToken == null) {
            throw new IllegalStateException("Токен не был получен в предыдущих тестах");
        }

        givenAuthenticatedRequest(registeredUserToken)
        .when()
            .post("/user/initiate-parsing")
        .then()
            .statusCode(anyOf(is(200), is(500)))             .contentType("application/json")
            .body("message", notNullValue());
            
        System.out.println("✅ Доступ к защищенным эндпоинтам с токеном работает");
    }

    @Test
    @Order(7)
    @DisplayName("Функциональный тест: Отказ доступа к защищенным эндпоинтам без токена")
    void shouldRejectAccessToProtectedEndpointsWithoutToken() {
        givenJsonRequest()
        .when()
            .post("/user/initiate-parsing")
        .then()
            .statusCode(401);
            
        System.out.println("✅ Доступ к защищенным эндпоинтам без токена корректно заблокирован");
    }

    @Test
    @Order(8)
    @DisplayName("Функциональный тест: Обработка некорректного JSON")
    void shouldHandleMalformedJson() {
        String malformedJson = "{\"email\": \"test@example.com\", \"password\":}";

        givenJsonRequest()
            .body(malformedJson)
        .when()
            .post("/auth/register")
        .then()
            .statusCode(400);
            
        System.out.println("✅ Некорректный JSON обрабатывается правильно");
    }
} 