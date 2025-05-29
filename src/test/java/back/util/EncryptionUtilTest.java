package back.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptionUtil Unit Tests")
class EncryptionUtilTest {

    private EncryptionUtil encryptionUtil;
    private final String TEST_SECRET_KEY = "AES_ENCRYPTION_KEY_16BYTES_SECRET";

    @BeforeEach
    void setUp() {
        encryptionUtil = new EncryptionUtil();
        ReflectionTestUtils.setField(encryptionUtil, "secretKey", TEST_SECRET_KEY);
    }

    @Test
    @DisplayName("Шифрование и дешифрование пароля")
    void encryptDecrypt_ValidPassword_ReturnsOriginal() {
        String originalPassword = "testPassword123";

        String encrypted = encryptionUtil.encryptPassword(originalPassword);
        String decrypted = encryptionUtil.decryptPassword(encrypted);

        assertNotNull(encrypted);
        assertNotEquals(originalPassword, encrypted);
        assertEquals(originalPassword, decrypted);
    }

    @Test
    @DisplayName("Шифрование пустого пароля")
    void encryptDecrypt_EmptyPassword_HandlesCorrectly() {
        String emptyPassword = "";

        String encrypted = encryptionUtil.encryptPassword(emptyPassword);
        String decrypted = encryptionUtil.decryptPassword(encrypted);

        assertEquals(emptyPassword, decrypted);
    }

    @Test
    @DisplayName("Дешифрование невалидных данных")
    void decryptPassword_InvalidData_ThrowsException() {
        String invalidEncrypted = "invalid_base64_data";

        assertThrows(RuntimeException.class, () -> {
            encryptionUtil.decryptPassword(invalidEncrypted);
        });
    }
} 