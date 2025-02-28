package back.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

  @Bean
  public PasswordEncoder passwordEncoder(CustomPasswordEncoder customPasswordEncoder) {
    return customPasswordEncoder;
  }
}