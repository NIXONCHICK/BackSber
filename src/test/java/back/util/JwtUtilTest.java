package back.util;

import back.entities.Person;
import back.entities.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtUtil Unit Tests")
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private Person testUser;
    private Map<String, Object> testClaims;
    
    private final String TEST_SECRET_KEY = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long TEST_EXPIRATION_MS = 3600000;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        
        ReflectionTestUtils.setField(jwtUtil, "secretKey", TEST_SECRET_KEY);
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", TEST_EXPIRATION_MS);

        testUser = new Person();
        testUser.setId(1L);
        testUser.setEmail("test.student@sfedu.ru");
        testUser.setRole(Role.STUDENT);

        testClaims = new HashMap<>();
        testClaims.put("role", "STUDENT");
        testClaims.put("userId", 1L);
    }

    @Test
    @DisplayName("Генерация токена с валидными данными")
    void generateToken_ValidUserAndClaims_ReturnsValidToken() {
        String token = jwtUtil.generateToken(testClaims, testUser);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.contains("."));
        
        String extractedUsername = jwtUtil.extractUsername(token);
        assertEquals(testUser.getEmail(), extractedUsername);
    }

    @Test
    @DisplayName("Генерация токена с пустыми claims")
    void generateToken_EmptyClaims_ReturnsValidToken() {
        Map<String, Object> emptyClaims = new HashMap<>();

        String token = jwtUtil.generateToken(emptyClaims, testUser);

        assertNotNull(token);
        assertEquals(testUser.getEmail(), jwtUtil.extractUsername(token));
    }

    @Test
    @DisplayName("Извлечение username из токена")
    void extractUsername_ValidToken_ReturnsUsername() {
        String token = jwtUtil.generateToken(testClaims, testUser);

        String extractedUsername = jwtUtil.extractUsername(token);

        assertEquals(testUser.getEmail(), extractedUsername);
    }

    @Test
    @DisplayName("Извлечение userId из токена")
    void extractUserId_ValidToken_ReturnsUserId() {
        String token = jwtUtil.generateToken(testClaims, testUser);

        Long extractedUserId = jwtUtil.extractUserId(token);

        assertEquals(1L, extractedUserId);
    }

    @Test
    @DisplayName("Извлечение даты истечения из токена")
    void extractExpiration_ValidToken_ReturnsExpirationDate() {
        String token = jwtUtil.generateToken(testClaims, testUser);
        long currentTime = System.currentTimeMillis();

        Date expiration = jwtUtil.extractExpiration(token);

        assertNotNull(expiration);
        assertTrue(expiration.getTime() > currentTime);
        assertTrue(expiration.getTime() <= currentTime + TEST_EXPIRATION_MS + 1000);
    }

    @Test
    @DisplayName("Извлечение custom claim из токена")
    void extractClaim_CustomClaim_ReturnsCorrectValue() {
        testClaims.put("customField", "customValue");
        String token = jwtUtil.generateToken(testClaims, testUser);

        String customValue = jwtUtil.extractClaim(token, claims -> claims.get("customField", String.class));

        assertEquals("customValue", customValue);
    }

    @Test
    @DisplayName("Валидация корректного токена")
    void validateToken_ValidToken_ReturnsTrue() {
        String token = jwtUtil.generateToken(testClaims, testUser);

        Boolean isValid = jwtUtil.validateToken(token, testUser);

        assertTrue(isValid);
    }

    @Test
    @DisplayName("Валидация токена с неправильным пользователем")
    void validateToken_WrongUser_ReturnsFalse() {
        String token = jwtUtil.generateToken(testClaims, testUser);
        
        Person anotherUser = new Person();
        anotherUser.setEmail("another.student@sfedu.ru");

        Boolean isValid = jwtUtil.validateToken(token, anotherUser);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("Валидация истекшего токена")
    void validateToken_ExpiredToken_ReturnsFalse() {
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", -1000);
        String expiredToken = jwtUtil.generateToken(testClaims, testUser);
        
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", TEST_EXPIRATION_MS);

        assertThrows(ExpiredJwtException.class, () -> {
            jwtUtil.validateToken(expiredToken, testUser);
        });
    }

    @Test
    @DisplayName("Извлечение данных из невалидного токена")
    void extractUsername_InvalidToken_ThrowsException() {
        String invalidToken = "invalid.jwt.token";

        assertThrows(MalformedJwtException.class, () -> {
            jwtUtil.extractUsername(invalidToken);
        });
    }

    @Test
    @DisplayName("Извлечение данных из токена с неправильной подписью")
    void extractUsername_WrongSignature_ThrowsException() {
        String tokenWithWrongSignature = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiaWF0IjoxNjE2MjM5MDIyfQ.wrong_signature";

        assertThrows(SignatureException.class, () -> {
            jwtUtil.extractUsername(tokenWithWrongSignature);
        });
    }

    @Test
    @DisplayName("Извлечение userId из токена без userId claim")
    void extractUserId_TokenWithoutUserId_ReturnsNull() {
        Map<String, Object> claimsWithoutUserId = new HashMap<>();
        claimsWithoutUserId.put("role", "STUDENT");
        String token = jwtUtil.generateToken(claimsWithoutUserId, testUser);

        Long extractedUserId = jwtUtil.extractUserId(token);

        assertNull(extractedUserId);
    }

    @Test
    @DisplayName("Генерация разных токенов для одного пользователя")
    void generateToken_SameUser_GeneratesDifferentTokens() {
        String token1 = jwtUtil.generateToken(testClaims, testUser);
        String token2 = jwtUtil.generateToken(testClaims, testUser);

        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("Проверка структуры JWT токена")
    void generateToken_ValidToken_HasCorrectStructure() {
        String token = jwtUtil.generateToken(testClaims, testUser);

        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        
        for (String part : parts) {
            assertFalse(part.isEmpty());
        }
    }

    @Test
    @DisplayName("Проверка извлечения всех claims из токена")
    void extractAllClaims_ValidToken_ReturnsAllClaims() {
        testClaims.put("customClaim", "customValue");
        String token = jwtUtil.generateToken(testClaims, testUser);

        String role = jwtUtil.extractClaim(token, claims -> claims.get("role", String.class));
        Long userId = jwtUtil.extractClaim(token, claims -> claims.get("userId", Long.class));
        String customClaim = jwtUtil.extractClaim(token, claims -> claims.get("customClaim", String.class));

        assertEquals("STUDENT", role);
        assertEquals(1L, userId);
        assertEquals("customValue", customClaim);
    }

    @Test
    @DisplayName("Проверка времени жизни токена")
    void generateToken_ValidToken_HasCorrectExpirationTime() {
        long beforeGeneration = System.currentTimeMillis();
        String token = jwtUtil.generateToken(testClaims, testUser);
        long afterGeneration = System.currentTimeMillis();

        Date expiration = jwtUtil.extractExpiration(token);
        long expirationTime = expiration.getTime();

        assertTrue(expirationTime >= beforeGeneration + TEST_EXPIRATION_MS);
        assertTrue(expirationTime <= afterGeneration + TEST_EXPIRATION_MS);
    }
} 