package back.config;

import back.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomPasswordEncoder implements PasswordEncoder {

  private final EncryptionUtil encryptionUtil;

  @Override
  public String encode(CharSequence rawPassword) {
    return encryptionUtil.encryptPassword(rawPassword.toString());
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    String decryptedPassword = encryptionUtil.decryptPassword(encodedPassword);
    return decryptedPassword.equals(rawPassword.toString());
  }
}