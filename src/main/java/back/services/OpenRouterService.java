package back.services;

import back.dto.TaskTimeEstimateResponse;
import back.entities.Task;
import back.entities.TaskAttachment;
import back.repositories.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OpenRouterService {
    
    private final TaskRepository taskRepository;
    private final MoodleAssignmentService moodleAssignmentService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${openrouter.api.key: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}")
    private String apiKey;
    
    @Value("${openrouter.api.url:https://openrouter.ai/api/v1/chat/completions}")
    private String apiUrl;
    
    @Value("${openrouter.model:deepseek/deepseek-chat:free}")
    private String model;
    
    @Value("${app.name:ИКТИБ Платформа}")
    private String appName;
    
    @Value("${app.url:https://platform.ictis.sfedu.ru}")
    private String appUrl;
    

    public TaskTimeEstimateResponse getTaskTimeEstimate(Long taskId, Long userId) throws Exception {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Задание не найдено"));
        
        if (task.getEstimatedMinutes() != null && task.getTimeEstimateExplanation() != null) {
            log.info("Найдена существующая оценка времени для задания с ID: {}, оценка: {} минут", 
                    taskId, task.getEstimatedMinutes());
            
            return TaskTimeEstimateResponse.builder()
                    .taskId(task.getId())
                    .taskName(task.getName())
                    .estimatedMinutes(task.getEstimatedMinutes())
                    .explanation(task.getTimeEstimateExplanation())
                    .createdAt(task.getTimeEstimateCreatedAt())
                    .fromCache(true)
                    .build();
        }
        
        log.info("Оценка времени не найдена в базе данных, запрашиваем у OpenRouter API");
        String context = getTaskContext(task, userId);
        OpenRouterResponse openRouterResponse = askOpenRouterForTimeEstimate(context, task.getName());
        
        task.setEstimatedMinutes(openRouterResponse.getEstimatedMinutes());
        task.setTimeEstimateExplanation(openRouterResponse.getExplanation());
        task.setTimeEstimateCreatedAt(new Date());
        taskRepository.save(task);
        
        return TaskTimeEstimateResponse.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .estimatedMinutes(openRouterResponse.getEstimatedMinutes())
                .explanation(openRouterResponse.getExplanation())
                .createdAt(task.getTimeEstimateCreatedAt())
                .fromCache(false)
                .build();
    }
    

    public TaskTimeEstimateResponse refreshTaskTimeEstimate(Long taskId, Long userId) throws Exception {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Задание не найдено"));
        
        String context = getTaskContext(task, userId);
        OpenRouterResponse openRouterResponse = askOpenRouterForTimeEstimate(context, task.getName());
        
        task.setEstimatedMinutes(openRouterResponse.getEstimatedMinutes());
        task.setTimeEstimateExplanation(openRouterResponse.getExplanation());
        task.setTimeEstimateCreatedAt(new Date());
        taskRepository.save(task);
        
        return TaskTimeEstimateResponse.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .estimatedMinutes(openRouterResponse.getEstimatedMinutes())
                .explanation(openRouterResponse.getExplanation())
                .createdAt(task.getTimeEstimateCreatedAt())
                .fromCache(false)
                .build();
    }
    

    private String getTaskContext(Task task, Long userId) throws Exception {
        try {
            return moodleAssignmentService.analyzeAssignment(task.getId(), userId).getExtractedText();
        } catch (Exception e) {
            log.warn("Не удалось получить полный контекст задания через Moodle: {}", e.getMessage());
            
            StringBuilder allText = new StringBuilder();
            
            if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                allText.append("===== ОПИСАНИЕ ЗАДАНИЯ =====\n\n");
                allText.append(task.getDescription()).append("\n\n");
            }
            
            if (task.getAttachments() != null && !task.getAttachments().isEmpty()) {
                log.info("Используем информацию о {} прикрепленных файлах из базы данных", task.getAttachments().size());
                
                for (TaskAttachment attachment : task.getAttachments()) {
                    allText.append("===== ФАЙЛ: ").append(attachment.getFileName()).append(" =====\n\n");
                    allText.append("Тип файла: ").append(attachment.getFileExtension()).append("\n");
                    allText.append("URL файла: ").append(attachment.getFileUrl()).append("\n\n");
                    
                    allText.append("Содержимое файла недоступно для анализа - файл может содержать код, данные или документацию.\n\n");
                }
            } else {
                allText.append("В задании нет прикрепленных файлов.\n\n");
            }
            
            if (task.getSubject() != null) {
                allText.append("Предмет: ").append(task.getSubject().getName()).append("\n\n");
            }
            
            if (task.getDeadline() != null) {
                allText.append("Дедлайн: ").append(task.getDeadline()).append("\n\n");
            }
            
            log.info("Сформирован альтернативный контекст задания длиной {} символов", allText.length());
            return allText.toString();
        }
    }
    

    private OpenRouterResponse askOpenRouterForTimeEstimate(String context, String taskName) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            headers.set("HTTP-Referer", appUrl);
            headers.set("X-Title", appName);
            
            taskName = (taskName != null && !taskName.isEmpty()) ? taskName : "Задание";
            
            log.info("Отправляем запрос с контекстом размером {} символов для задания: '{}'", context.length(), taskName);
            
            String systemPrompt = "Ты эксперт по оценке сложности и времени выполнения учебных заданий для студентов. " +
                    "Твоя задача - внимательно прочитать описание задания и все прикрепленные файлы, а затем " +
                    "оценить примерное время выполнения задания в минутах. " +
                    "ВАЖНО: НЕ оценивай время по названию задания - оно может быть неинформативным. " +
                    "Учитывай все детали из содержания задания и прикрепленных файлов. " +
                    "Обрати особое внимание на следующие факторы:\n" +
                    "1. Сложность требуемой работы (программирование, отчет, расчеты и т.д.)\n" +
                    "2. Объем и детализацию описания и требований\n" +
                    "3. Количество и тип прикрепленных файлов\n" +
                    "4. Необходимость создания кода, схем, отчетов\n" +
                    "5. ОБЯЗАТЕЛЬНО учитывай время, которое потребуется на изучение теоретического материала перед выполнением практической части\n" +
                    "6. ОБЯЗАТЕЛЬНО учитывай время, необходимое для оформления отчета по заданию (включая написание текста, оформление таблиц, графиков, схем, код, выводы и т.д.)\n" +
                    "Помни, что студенты обычно последовательно проходят следующие этапы:\n" +
                    "- Изучение теоретических материалов и требований к заданию (часто требует значительного времени)\n" +
                    "- Выполнение практической части задания\n" +
                    "- Оформление отчета (часто занимает 30-50% от общего времени выполнения)\n" +
                    "Учитывай, что задания предназначены для студентов технических специальностей ИКТИБ ЮФУ. " +
                    "Ответ дай в формате JSON с полями: estimatedMinutes (целое число минут) и " +
                    "explanation (краткое объяснение твоей оценки на русском языке).\n\n" +
                    "ОЧЕНЬ ВАЖНО: В поле explanation используй дружелюбный, неформальный тон, обращайся к студенту на 'ты', " +
                    "будто объясняешь своему другу. Используй простой разговорный стиль, без излишнего формализма. " +
                    "При этом ВАЖНО: используй гендерно-нейтральные формулировки, которые не указывают на пол студента. " +
                    "Избегай форм глаголов и прилагательных в прошедшем времени, которые выявляют пол. " +
                    "Используй инфинитивы, настоящее и будущее время, обобщенные формулировки." +
                    "ВАЖНО: в поле explanation опиши только, ЧТО включает в себя задание (основные этапы работы), " +
                    "но НЕ указывай конкретное время для каждого этапа или для всего задания. Оценка времени уже есть в поле estimatedMinutes." +
                    "ОБЯЗАТЕЛЬНО в начале объяснения укажи уровень сложности задания (например: 'Это лёгкое/среднее/сложное задание', " + 
                    "'Задание средней сложности', 'Это довольно простое задание', 'Задание повышенной сложности' и т.п.). " +
                    "Оценивай сложность по шкале: очень лёгкое, лёгкое, среднее, выше среднего, сложное, очень сложное.";
            
            String userPrompt = "Внимательно проанализируй следующее задание.\n\n" +
                    "Название (не главное): " + taskName + "\n\n" +
                    "ПОДРОБНОЕ СОДЕРЖАНИЕ ЗАДАНИЯ (самое важное):\n\n" + context + "\n\n" +
                    "На основе СОДЕРЖАНИЯ (а не названия) оцени реальное время выполнения этого задания в минутах.\n\n" +
                    "В оценке ОБЯЗАТЕЛЬНО учти:\n" +
                    "1. Время на изучение теоретического материала перед практической работой\n" +
                    "2. Время на оформление подробного отчета о проделанной работе\n" +
                    "3. Время на отладку и исправление ошибок\n" +
                    "Давай максимально реалистичную оценку с точки зрения среднего студента.\n\n" +
                    "В объяснении обязательно используй дружелюбный тон, обращайся на 'ты'. " +
                    "Пиши словно помогаешь другу оценить задание. Очень важно: используй гендерно-нейтральные формулировки, не указывай на пол студента - " +
                    "не используй формы типа 'сделал/сделала', 'мог бы/могла бы', а используй конструкции без указания пола: 'можно сделать', 'стоит изучить', 'тебе понадобится', 'нужно будет'. " +
                    "ВАЖНО: в объяснении опиши только, что включает в себя задание (теория, практика, отчет и т.д.), но НЕ указывай время выполнения для каждого этапа или для всего задания. Например, вместо 'На изучение теории уйдет 1 час' напиши 'Задание включает изучение теоретического материала'." +
                    "ОБЯЗАТЕЛЬНО: в начале объяснения укажи уровень сложности задания (легкое, среднее, сложное и т.д.).";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.0);
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            );
            
            requestBody.put("messages", messages);
            
            log.info("Отправляем запрос к OpenRouter API для оценки времени выполнения задания: {}", taskName);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    String responseBody = response.getBody();
                    log.debug("Получен ответ от OpenRouter API: {}", responseBody);
                    JsonNode rootNode = objectMapper.readTree(responseBody);
                    String content = rootNode.path("choices").get(0).path("message").path("content").asText();
                    
                    OpenRouterResponse openRouterResponse = parseOpenRouterResponse(content);
                    log.info("OpenRouter API оценил время выполнения задания '{}' в {} минут, объяснение: {}", 
                            taskName, openRouterResponse.getEstimatedMinutes(), openRouterResponse.getExplanation());
                    return openRouterResponse;
                } else {
                    log.error("Ошибка при запросе к OpenRouter API: {}", response.getStatusCode());
                    throw new RuntimeException("Ошибка при запросе к OpenRouter API: " + response.getStatusCode());
                }
            } catch (Exception e) {
                if (context.length() > 20000) {
                    log.warn("Произошла ошибка при обработке полного контекста: {}. Пробуем отправить сокращенный контекст.", e.getMessage());
                    
                    String shortenedContext = context;
                    
                    shortenedContext = context.substring(0, 10000) +
                            "\n\n... [текст сокращен из-за ограничений API, пропущено " + 
                            (context.length() - 20000) + " символов] ...\n\n" + 
                            context.substring(context.length() - 10000);
                    
                    String shortenedUserPrompt = "Внимательно проанализируй следующее задание.\n\n" +
                            "Название (не главное): " + taskName + "\n\n" +
                            "ПОДРОБНОЕ СОДЕРЖАНИЕ ЗАДАНИЯ (самое важное):\n\n" + shortenedContext + "\n\n" +
                            "На основе СОДЕРЖАНИЯ (а не названия) оцени реальное время выполнения этого задания в минутах.\n\n" +
                            "В оценке ОБЯЗАТЕЛЬНО учти:\n" +
                            "1. Время на изучение теоретического материала перед практической работой\n" +
                            "2. Время на оформление подробного отчета о проделанной работе\n" +
                            "3. Время на отладку и исправление ошибок\n" +
                            "Давай максимально реалистичную оценку с точки зрения среднего студента.\n\n" +
                            "В объяснении обязательно используй дружелюбный тон, обращайся на 'ты', используй фразы типа 'смотри', 'тебе понадобится', 'советую', 'можно успеть', 'не переживай'. " +
                            "Пиши словно помогаешь другу оценить задание. Очень важно: используй гендерно-нейтральные формулировки, не указывай на пол студента - " +
                            "не используй формы типа 'сделал/сделала', 'мог бы/могла бы', а используй конструкции без указания пола: 'можно сделать', 'стоит изучить', 'тебе понадобится', 'нужно будет'. " +
                            "ВАЖНО: в объяснении опиши только, что включает в себя задание (теория, практика, отчет и т.д.), но НЕ указывай время выполнения для каждого этапа или для всего задания. Например, вместо 'На изучение теории уйдет 1 час' напиши 'Задание включает изучение теоретического материала'." +
                            "ОБЯЗАТЕЛЬНО: в начале объяснения укажи уровень сложности задания (легкое, среднее, сложное и т.д.).";
                    
                    List<Map<String, String>> shortenedMessages = List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", shortenedUserPrompt)
                    );
                    
                    requestBody.put("messages", shortenedMessages);
                    HttpEntity<Map<String, Object>> shortenedEntity = new HttpEntity<>(requestBody, headers);
                    
                    log.info("Повторная попытка с сокращенным контекстом размером {} символов", shortenedContext.length());
                    ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, shortenedEntity, String.class);
                    
                    if (response.getStatusCode() == HttpStatus.OK) {
                        String responseBody = response.getBody();
                        log.debug("Получен ответ от OpenRouter API со сокращенным контекстом: {}", responseBody);
                        JsonNode rootNode = objectMapper.readTree(responseBody);
                        String content = rootNode.path("choices").get(0).path("message").path("content").asText();
                        
                        OpenRouterResponse openRouterResponse = parseOpenRouterResponse(content);
                        log.info("OpenRouter API оценил время выполнения задания '{}' в {} минут (со сокращенным контекстом), объяснение: {}", 
                                taskName, openRouterResponse.getEstimatedMinutes(), openRouterResponse.getExplanation());
                        return openRouterResponse;
                    } else {
                        log.error("Ошибка при запросе к OpenRouter API со сокращенным контекстом: {}", response.getStatusCode());
                        throw new RuntimeException("Ошибка при запросе к OpenRouter API: " + response.getStatusCode());
                    }
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке запроса к OpenRouter API: {}", e.getMessage(), e);
            return new OpenRouterResponse(120, "Примерная оценка по умолчанию: задание средней сложности, требующее около 2 часов работы. Точная оценка невозможна из-за ошибки API.");
        }
    }
    

    private OpenRouterResponse parseOpenRouterResponse(String content) {
        try {
            String jsonContent = content.trim();
            
            if (jsonContent.startsWith("```json") && jsonContent.endsWith("```")) {
                jsonContent = jsonContent.substring(7, jsonContent.length() - 3).trim();
            } else if (jsonContent.startsWith("```") && jsonContent.endsWith("```")) {
                jsonContent = jsonContent.substring(3, jsonContent.length() - 3).trim();
            }
            
            JsonNode jsonNode = objectMapper.readTree(jsonContent);
            int estimatedMinutes = jsonNode.path("estimatedMinutes").asInt();
            String explanation = jsonNode.path("explanation").asText();
            return new OpenRouterResponse(estimatedMinutes, explanation);
        } catch (Exception e) {
            log.error("Ошибка при парсинге ответа от OpenRouter API: {}", e.getMessage());
            int estimatedMinutes = extractMinutesFromText(content);
            return new OpenRouterResponse(estimatedMinutes, "Извлечено из текста ответа: " + content.substring(0, Math.min(200, content.length())));
        }
    }
    
    private int extractMinutesFromText(String text) {
        try {
            if (text.matches(".*\\b(\\d+)\\s*час.*")) {
                String[] parts = text.split("\\b(\\d+)\\s*час");
                for (String part : parts) {
                    if (part.matches(".*\\d+.*")) {
                        int hours = Integer.parseInt(part.replaceAll("[^0-9]", ""));
                        return hours * 60;
                    }
                }
            }
            
            if (text.matches(".*\\b(\\d+)\\s*минут.*")) {
                String[] parts = text.split("\\b(\\d+)\\s*минут");
                for (String part : parts) {
                    if (part.matches(".*\\d+.*")) {
                        return Integer.parseInt(part.replaceAll("[^0-9]", ""));
                    }
                }
            }
            
            String[] words = text.split("\\s+");
            for (String word : words) {
                if (word.matches("\\d+")) {
                    int num = Integer.parseInt(word);
                    if (num < 24) {
                        return num * 60;
                    } else {
                        return num;
                    }
                }
            }
            
            return 120;
        } catch (Exception e) {
            log.error("Ошибка при извлечении минут из текста", e);
            return 120;
        }
    }

    private static class OpenRouterResponse {
        private final int estimatedMinutes;
        private final String explanation;

        public OpenRouterResponse(int estimatedMinutes, String explanation) {
            this.estimatedMinutes = estimatedMinutes;
            this.explanation = explanation;
        }

        public int getEstimatedMinutes() {
            return estimatedMinutes;
        }

        public String getExplanation() {
            return explanation;
        }
    }
} 