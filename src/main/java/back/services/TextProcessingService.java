package back.services;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class TextProcessingService {
    
    private final Tika tika = new Tika();
    private HuggingFaceTokenizer tokenizer;
    
    @PostConstruct
    public void init() {
        try {
            Map<String, String> options = new HashMap<>();
            options.put("modelMaxLength", "32768");
            options.put("maxLength", "32768");
            
            tokenizer = HuggingFaceTokenizer.newInstance("gpt2", options);
            log.info("GPT-2 токенизатор успешно инициализирован с увеличенным лимитом токенов (32768)");
            
            testTokenizer();
        } catch (Exception e) {
            log.error("Ошибка при инициализации токенизатора", e);
            throw new RuntimeException("Не удалось инициализировать токенизатор", e);
        }
    }
    

    private void testTokenizer() {
        try {
            log.info("Начало тестирования токенизатора...");
            
            String testText = "Это тестовый текст для проверки работы токенизатора. "
                + "Он должен содержать достаточное количество слов, чтобы убедиться, "
                + "что токенизатор не обрезает текст на фиксированном значении.";
            
            int tokens = countTokens(testText);
            log.info("ТЕСТ ТОКЕНИЗАТОРА: короткий текст ({} символов) содержит {} токенов",
                    testText.length(), tokens);
            
            String longText = testText.repeat(20);
            int longTokens = countTokens(longText);
            log.info("ТЕСТ ТОКЕНИЗАТОРА: длинный текст ({} символов) содержит {} токенов", 
                    longText.length(), longTokens);
            
            if (longTokens > 512) {
                double ratio = 512.0 / longTokens;
                int charEstimate = (int)(longText.length() * ratio);
                String mediumText = longText.substring(0, charEstimate);
                
                int mediumTokens = countTokens(mediumText);
                log.info("ТЕСТ ТОКЕНИЗАТОРА: текст средней длины ({} символов) содержит {} токенов", 
                        mediumText.length(), mediumTokens);
            }
            
            log.info("Тестирование токенизатора завершено успешно");
        } catch (Exception e) {
            log.error("Ошибка при тестировании токенизатора", e);
        }
    }
    

    public String extractTextFromFile(File file) {
        try (InputStream is = Files.newInputStream(file.toPath())) {
            String extractedText = tika.parseToString(is);
            log.info("Извлечено {} символов из файла {}", 
                     extractedText.length(), 
                     file.getName());
            
            if (!extractedText.isEmpty()) {
                log.info("Начало извлеченного текста: {}", 
                        extractedText.substring(0, Math.min(100, extractedText.length())));
            }
            
            return extractedText;
        } catch (Exception e) {
            log.error("Ошибка при извлечении текста из файла: {}", file.getName(), e);
            return "Ошибка при извлечении текста: " + e.getMessage();
        }
    }
    

    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        try {
            var encoding = tokenizer.encode(text);
            int tokenCount = encoding.getIds().length;
            
            log.info("Текст длиной {} символов содержит {} токенов", 
                    text.length(), tokenCount);
            
            return tokenCount;
        } catch (Exception e) {
            log.error("Ошибка при подсчете токенов: {}", e.getMessage());
            
            String[] words = text.split("\\s+");
            int estimate = (int) (words.length * 1.3);
            log.info("Примерная оценка токенов: {} (на основе {} слов)", estimate, words.length);
            return estimate;
        }
    }
} 