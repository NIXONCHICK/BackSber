package back.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class EncryptionUtil {
    
    @Value("${app.encryption.secret-key}")
    private String secretKey;
    

    private byte[] getAdjustedKey() {
        byte[] originalKey = secretKey.getBytes(StandardCharsets.UTF_8);
        if (originalKey.length == 16 || originalKey.length == 24 || originalKey.length == 32) {
            return originalKey;
        }
        
        byte[] adjustedKey = new byte[16];
        
        System.arraycopy(
            originalKey, 
            0, 
            adjustedKey, 
            0, 
            Math.min(originalKey.length, 16));
        
        return adjustedKey;
    }
    

    public String decryptPassword(String encryptedPassword) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    getAdjustedKey(), "AES");
            
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedPassword);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при расшифровке пароля", e);
        }
    }
    

    public String encryptPassword(String password) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    getAdjustedKey(), "AES");
            
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            
            byte[] encryptedBytes = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при шифровании пароля", e);
        }
    }
}