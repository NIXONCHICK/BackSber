package back.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;


@TestPropertySource(properties = {
    "jwt.secret-key=performance-test-secret-key-for-load-testing-very-long-key-minimum-256-bits",
    "jwt.expiration-ms=3600000"
})
@DisplayName("Нагрузочные тесты аутентификации")
class AuthPerformanceTest extends BasePerformanceTest {

    private static final AtomicInteger userCounter = new AtomicInteger(1);

    @Test
    @DisplayName("Нагрузочный тест: Массовая регистрация пользователей")
    void testMassiveUserRegistration() throws InterruptedException {
        int threadCount = 10;
        int requestsPerThread = 20;
        
        PerformanceMetrics metrics = executeLoadTest(
            "Массовая регистрация пользователей",
            threadCount,
            requestsPerThread,
            this::performUserRegistration
        );

        assertTrue(metrics.getSuccessRate() > 95.0,
                  "Процент успешных регистраций должен быть выше 95%");
        assertTrue(metrics.getAverageResponseTime() < 2000, 
                  "Среднее время отклика должно быть меньше 2 секунд");
        assertTrue(metrics.getThroughput() > 5.0, 
                  "Пропускная способность должна быть больше 5 запросов/сек");
    }

    @Test
    @DisplayName("Нагрузочный тест: Одновременные логины")
    void testConcurrentLogins() throws InterruptedException {
        String testEmail = "load.test.user@sfedu.ru";
        String testPassword = "loadTestPassword123";
        createTestUser(testEmail, testPassword);

        int threadCount = 15;
        int requestsPerThread = 15;
        
        PerformanceMetrics metrics = executeLoadTest(
            "Одновременные логины",
            threadCount,
            requestsPerThread,
            () -> performUserLogin(testEmail, testPassword)
        );

        assertTrue(metrics.getSuccessRate() > 90.0,
                  "Процент успешных логинов должен быть выше 90%");
        assertTrue(metrics.getAverageResponseTime() < 1500, 
                  "Среднее время отклика должно быть меньше 1.5 секунд");
        assertTrue(metrics.getThroughput() > 8.0, 
                  "Пропускная способность должна быть больше 8 запросов/сек");
    }

    @Test
    @DisplayName("Нагрузочный тест: Смешанная нагрузка (регистрация + логин)")
    void testMixedLoad() throws InterruptedException {
        String baseEmail = "mixed.load.user@sfedu.ru";
        String basePassword = "mixedLoadPassword123";
        createTestUser(baseEmail, basePassword);

        int threadCount = 12;
        int requestsPerThread = 10;
        
        PerformanceMetrics metrics = executeLoadTest(
            "Смешанная нагрузка (50% регистрация, 50% логин)",
            threadCount,
            requestsPerThread,
            () -> {
                if (Math.random() < 0.5) {
                    performUserRegistration();
                } else {
                    performUserLogin(baseEmail, basePassword);
                }
            }
        );

        assertTrue(metrics.getSuccessRate() > 85.0,
                  "Процент успешных запросов должен быть выше 85%");
        assertTrue(metrics.getAverageResponseTime() < 3000, 
                  "Среднее время отклика должно быть меньше 3 секунд");
        assertTrue(metrics.getThroughput() > 4.0, 
                  "Пропускная способность должна быть больше 4 запросов/сек");
    }

    @Test
    @DisplayName("Нагрузочный тест: Стресс-тест с высокой нагрузкой")
    void testHighLoadStress() throws InterruptedException {
        String stressEmail = "stress.test.user@sfedu.ru";
        String stressPassword = "stressTestPassword123";
        createTestUser(stressEmail, stressPassword);

        int threadCount = 25;
        int requestsPerThread = 8;
        
        PerformanceMetrics metrics = executeLoadTest(
            "Стресс-тест с высокой нагрузкой",
            threadCount,
            requestsPerThread,
            () -> performUserLogin(stressEmail, stressPassword)
        );

        assertTrue(metrics.getSuccessRate() > 70.0,
                  "Даже под высокой нагрузкой процент успехов должен быть выше 70%");
        assertTrue(metrics.getAverageResponseTime() < 5000, 
                  "Среднее время отклика не должно превышать 5 секунд");
        assertTrue(metrics.getErrorCount() < metrics.getTotalRequests() * 0.3, 
                  "Количество ошибок не должно превышать 30% от общего числа запросов");
        
        System.out.println("🏆 Система выдержала стресс-тест!");
    }

    @Test
    @DisplayName("Нагрузочный тест: Производительность валидации")
    void testValidationPerformance() throws InterruptedException {
        int threadCount = 8;
        int requestsPerThread = 25;
        
        PerformanceMetrics metrics = executeLoadTest(
            "Производительность валидации",
            threadCount,
            requestsPerThread,
            this::performInvalidRegistration
        );

        assertTrue(metrics.getAverageResponseTime() < 500,
                  "Валидация должна работать быстро (< 500мс)");
        assertTrue(metrics.getThroughput() > 15.0, 
                  "Пропускная способность валидации должна быть высокой");
        
        System.out.println("⚡ Валидация работает эффективно!");
    }


    private void performUserRegistration() {
        int userId = userCounter.getAndIncrement();
        String email = "perf.user." + userId + "@sfedu.ru";
        String password = "perfTestPassword" + userId;

        Map<String, Object> requestBody = Map.of(
            "email", email,
            "password", password,
            "role", "STUDENT"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/auth/register", 
            request, 
            Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Registration failed: " + response.getStatusCode());
        }
    }


    private void performUserLogin(String email, String password) {
        Map<String, Object> requestBody = Map.of(
            "email", email,
            "password", password
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/auth/login", 
            request, 
            Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Login failed: " + response.getStatusCode());
        }
    }


    private void performInvalidRegistration() {
        Map<String, Object> requestBody = Map.of(
            "email", "invalid-email-format",
            "password", "",
            "role", "STUDENT"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/auth/register", 
            request, 
            Map.class
        );

        // Ожидаем ошибку валидации
        if (!response.getStatusCode().is4xxClientError()) {
            throw new RuntimeException("Expected validation error");
        }
    }


    private void createTestUser(String email, String password) {
        try {
            Map<String, Object> requestBody = Map.of(
                "email", email,
                "password", password,
                "role", "STUDENT"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            restTemplate.postForEntity(baseUrl + "/auth/register", request, Map.class);
        } catch (Exception e) {
        }
    }
} 