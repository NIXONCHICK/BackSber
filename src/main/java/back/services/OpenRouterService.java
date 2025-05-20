package back.services;

import back.dto.TaskTimeEstimateResponse;
import back.entities.Task;
import back.entities.TaskAttachment;
import back.entities.TaskSource;
import back.repositories.TaskRepository;
import back.repositories.StudentTaskAssignmentRepository;
import back.repositories.TaskGradingRepository;
import back.entities.StudentTaskAssignment;
import back.entities.TaskGrading;
import back.dto.TaskForStudyPlanDto;
import back.services.UserParsingService.ParsedTask;
import back.services.UserParsingService.ParsedAttachment;
import back.entities.Person;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OpenRouterService {

    private final TaskRepository taskRepository;
    private final MoodleAssignmentService moodleAssignmentService;
    private final StudentTaskAssignmentRepository studentTaskAssignmentRepository;
    private final TaskGradingRepository taskGradingRepository;
    private final TextProcessingService textProcessingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // OpenRouter Configuration
    @Value("${openrouter.api.key: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}")
    private String openRouterApiKey;

    @Value("${openrouter.api.url:https://openrouter.ai/api/v1/chat/completions}")
    private String openRouterApiUrl;

    @Value("${openrouter.model:deepseek/deepseek-chat-v3-0324:free}")
    private String openRouterModel;

    // Gemini API Configuration
    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.model.name:gemini-2.0-flash-lite-001}")
    private String geminiModelName;

    @Value("${gemini.api.base.url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String geminiApiBaseUrl;


    @Value("${app.name:ИКТИБ Платформа}")
    private String appName;

    @Value("${app.url:https://platform.ictis.sfedu.ru}")
    private String appUrl;

    public TaskTimeEstimateResponse getTaskTimeEstimate(Long taskId, Long userId) {
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

        log.info("Оценка времени не найдена в базе данных, запрашиваем у Gemini API");
        String context = getTaskContext(task, userId);
        OpenRouterResponse geminiResponse = askGeminiForTimeEstimate(context, task.getName());

        task.setEstimatedMinutes(geminiResponse.getEstimatedMinutes());
        task.setTimeEstimateExplanation(geminiResponse.getExplanation());
        task.setTimeEstimateCreatedAt(new Date());
        taskRepository.save(task);

        return TaskTimeEstimateResponse.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .estimatedMinutes(geminiResponse.getEstimatedMinutes())
                .explanation(geminiResponse.getExplanation())
                .createdAt(task.getTimeEstimateCreatedAt())
                .fromCache(false)
                .build();
    }


    public TaskTimeEstimateResponse refreshTaskTimeEstimate(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Задание не найдено"));

        String context = getTaskContext(task, userId);
        OpenRouterResponse geminiResponse = askGeminiForTimeEstimate(context, task.getName());

        task.setEstimatedMinutes(geminiResponse.getEstimatedMinutes());
        task.setTimeEstimateExplanation(geminiResponse.getExplanation());
        task.setTimeEstimateCreatedAt(new Date());
        taskRepository.save(task);

        return TaskTimeEstimateResponse.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .estimatedMinutes(geminiResponse.getEstimatedMinutes())
                .explanation(geminiResponse.getExplanation())
                .createdAt(task.getTimeEstimateCreatedAt())
                .fromCache(false)
                .build();
    }


    private String getTaskContext(Task task, Long userId) {
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


    private OpenRouterResponse askGeminiForTimeEstimate(String contextText, String taskName) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            taskName = (taskName != null && !taskName.isEmpty()) ? taskName : "Задание";
            log.info("Отправляем запрос Gemini API с контекстом размером {} символов для задания: '{}'", contextText.length(), taskName);

            // Формируем единый промпт для Gemini
            String prompt = "Ты эксперт по оценке сложности и времени выполнения учебных заданий для студентов ИКТИБ ЮФУ. " +
                    "Твоя задача - внимательно проанализировать СОДЕРЖАНИЕ задания (НЕ название) и оценить примерное время его выполнения в минутах. " +
                    "Учитывай следующие факторы: сложность работы (программирование, отчет, расчеты и т.д.), объем требований, " +
                    "количество и тип прикрепленных файлов (если есть информация о них), необходимость создания кода, схем, отчетов, " +
                    "время на изучение теории перед практикой, и время на оформление отчета (30-50% от общего времени). " +
                    "Ответ дай СТРОГО в формате JSON с двумя полями: \"estimatedMinutes\" (целое число минут) и \"explanation\" (краткое объяснение оценки на русском языке). " +
                    "В поле \"explanation\" используй дружелюбный, неформальный тон, обращайся к студенту на 'ты', гендерно-нейтрально. " +
                    "В начале объяснения ОБЯЗАТЕЛЬНО укажи предполагаемый уровень сложности (например: 'Это довольно простое задание', 'Задание средней сложности', 'Задание повышенной сложности'). " +
                    "В объяснении опиши, ЧТО включает в себя задание (основные этапы: теория, практика, отчет), но НЕ указывай время для каждого этапа или общее время (оно уже есть в estimatedMinutes). " +
                    "Пример JSON: {\\\"estimatedMinutes\\\": 120, \\\"explanation\\\": \\\"Это задание средней сложности. Тебе предстоит изучить теорию, затем выполнить практическую часть и подготовить отчет.\\\"} " +
                    "ПОДРОБНОЕ СОДЕРЖАНИЕ ЗАДАНИЯ (самое важное):\\n\\n" + contextText + "\\n\\n" +
                    "Название (не главное): " + taskName + "\\n\\n" +
                    "Оцени реальное время выполнения, учитывая изучение теории, практику и оформление отчета. Дай реалистичную оценку для среднего студента. " +
                    "ВАЖНО: Предыдущие оценки часто оказывались заниженными. Пожалуйста, подойди к оценке более тщательно и дай более щедрую (консервативную) оценку. " +
                    "Обязательно учти, что студенты могут столкнуться с непредвиденными сложностями, потратить дополнительное время на более глубокое изучение отдельных аспектов темы, " +
                    "на отладку практической части, а также на очень тщательное оформление финального отчета со всеми необходимыми элементами (введение, основная часть, детальные расчеты/описание кода, графики, таблицы, выводы, список литературы и т.д.), особенно если студент нацелен на высокую оценку. " +
                    "Помни, что полное понимание задачи, планирование, все этапы непосредственного выполнения (включая возможное написание кода, расчеты, создание схем) и, повторюсь, качественное оформление отчета требуют значительного времени, которое не стоит недооценивать. " +
                    "Помни, что средний студент не является экспертом, может впервые сталкиваться с некоторыми технологиями или методами, и ему потребуется время на 'раскачку' и преодоление кривой обучения. " +
                    "Настоятельно рекомендую закладывать некоторый буфер времени на непредвиденные обстоятельства, возможные ошибки в понимании требований, необходимость переделок и консультаций. Оценка должна это отражать. " +
                    "Не забывай, что оценка должна покрывать ВЕСЬ цикл работы: от момента получения задания и первого прочтения до полной сдачи готовой работы, включая все итерации правок и доработок, если таковые потребуются для достижения высокой оценки. " +
                    "Твоя цель – дать максимально безопасную и реалистичную оценку, которая поможет студенту реально спланировать свое время, а не создать ложное впечатление, что все можно сделать очень быстро. Лучше немного переоценить, чем сильно недооценить.";

            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(part));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.0);
            generationConfig.put("responseMimeType", "application/json");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(content));
            requestBody.put("generationConfig", generationConfig);

            String apiUrl = String.format("%s/%s:generateContent?key=%s", geminiApiBaseUrl, geminiModelName, geminiApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            try {
                ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    String responseBody = response.getBody();
                    log.debug("Получен ответ от Gemini API: {}", responseBody);
                    JsonNode rootNode = objectMapper.readTree(responseBody);

                    JsonNode candidatesNode = rootNode.path("candidates");
                    if (candidatesNode.isMissingNode() || !candidatesNode.isArray() || candidatesNode.isEmpty()) {
                        log.error("Gemini API вернул ответ без валидного поля 'candidates': {}", responseBody);
                        JsonNode promptFeedbackNode = rootNode.path("promptFeedback");
                        if (!promptFeedbackNode.isMissingNode() && promptFeedbackNode.path("blockReason").isTextual()) {
                             String blockReason = promptFeedbackNode.path("blockReason").asText();
                             String safetyRatings = promptFeedbackNode.path("safetyRatings").toString();
                             log.error("Промпт Gemini заблокирован. Причина: {}, Рейтинги безопасности: {}", blockReason, safetyRatings);
                             throw new RuntimeException("Запрос к Gemini API заблокирован из-за содержимого промпта. Причина: " + blockReason);
                        }
                        throw new RuntimeException("Gemini API вернул неверный формат ответа (отсутствует 'candidates').");
                    }

                    String jsonResponseText = candidatesNode.get(0).path("content").path("parts").get(0).path("text").asText();
                    if (jsonResponseText.isEmpty()) {
                         log.error("Gemini API вернул пустой текст в content.parts[0].text: {}", responseBody);
                         throw new RuntimeException("Gemini API вернул пустой ответ в ожидаемом поле.");
                    }
                    
                    OpenRouterResponse geminiJsonResponse = parseOpenRouterResponse(jsonResponseText);
                    log.info("Gemini API оценил время выполнения задания '{}' в {} минут, объяснение: {}",
                            taskName, geminiJsonResponse.getEstimatedMinutes(), geminiJsonResponse.getExplanation());
                    return geminiJsonResponse;
                } else {
                    log.error("Ошибка при запросе к Gemini API: {}. Ответ: {}", response.getStatusCode(), response.getBody());
                    throw new RuntimeException("Ошибка при запросе к Gemini API: " + response.getStatusCode() + " " + response.getBody());
                }
            } catch (Exception e) {
                // Уточненная логика сокращения контекста для Gemini
                boolean shouldRetryWithShortenedContext = contextText.length() > 20000 && 
                                                        (e.getMessage().toLowerCase().contains("заблокирован") || 
                                                         e.getMessage().toLowerCase().contains("размер запроса превышает лимит")); // Пример более общих условий

                if (shouldRetryWithShortenedContext) {
                     log.warn("Произошла ошибка при обработке полного контекста Gemini: {}. Пробуем отправить сокращенный контекст.", e.getMessage());
                    String shortenedContextText = contextText.substring(0, 10000) +
                            "\\n\\n... [текст сокращен из-за ограничений API/модели, пропущено " +
                            (contextText.length() - 20000) + " символов] ...\\n\\n" +
                            contextText.substring(contextText.length() - 10000);
                    
                    String shortenedPrompt = "Ты эксперт по оценке сложности и времени выполнения учебных заданий для студентов ИКТИБ ЮФУ. " +
                        "Твоя задача - внимательно проанализировать СОДЕРЖАНИЕ задания (НЕ название) и оценить примерное время его выполнения в минутах. " +
                        "Учитывай следующие факторы: сложность работы (программирование, отчет, расчеты и т.д.), объем требований, " +
                        "количество и тип прикрепленных файлов (если есть информация о них), необходимость создания кода, схем, отчетов, " +
                        "время на изучение теории перед практикой, и время на оформление отчета (30-50% от общего времени). " +
                        "Ответ дай СТРОГО в формате JSON с двумя полями: \"estimatedMinutes\" (целое число минут) и \"explanation\" (краткое объяснение оценки на русском языке). " +
                        "В поле \"explanation\" используй дружелюбный, неформальный тон, обращайся к студенту на 'ты', гендерно-нейтрально. " +
                        "В начале объяснения ОБЯЗАТЕЛЬНО укажи предполагаемый уровень сложности (например: 'Это довольно простое задание', 'Задание средней сложности', 'Задание повышенной сложности'). " +
                        "В объяснении опиши, ЧТО включает в себя задание (основные этапы: теория, практика, отчет), но НЕ указывай время для каждого этапа или общее время (оно уже есть в estimatedMinutes). " +
                        "Пример JSON: {\\\"estimatedMinutes\\\": 120, \\\"explanation\\\": \\\"Это задание средней сложности. Тебе предстоит изучить теорию, затем выполнить практическую часть и подготовить отчет.\\\"} " +
                        "ПОДРОБНОЕ СОДЕРЖАНИЕ ЗАДАНИЯ (самое важное):\\n\\n" + shortenedContextText + "\\n\\n" +
                        "Название (не главное): " + taskName + "\\n\\n" +
                        "Оцени реальное время выполнения, учитывая изучение теории, практику и оформление отчета. Дай реалистичную оценку для среднего студента. " +
                        "ВАЖНО: Предыдущие оценки часто оказывались заниженными. Пожалуйста, подойди к оценке более тщательно и дай более щедрую (консервативную) оценку. " +
                        "Обязательно учти, что студенты могут столкнуться с непредвиденными сложностями, потратить дополнительное время на более глубокое изучение отдельных аспектов темы, " +
                        "на отладку практической части, а также на очень тщательное оформление финального отчета со всеми необходимыми элементами (введение, основная часть, детальные расчеты/описание кода, графики, таблицы, выводы, список литературы и т.д.), особенно если студент нацелен на высокую оценку. " +
                        "Помни, что полное понимание задачи, планирование, все этапы непосредственного выполнения (включая возможное написание кода, расчеты, создание схем) и, повторюсь, качественное оформление отчета требуют значительного времени, которое не стоит недооценивать. " +
                        "Помни, что средний студент не является экспертом, может впервые сталкиваться с некоторыми технологиями или методами, и ему потребуется время на 'раскачку' и преодоление кривой обучения. " +
                        "Настоятельно рекомендую закладывать некоторый буфер времени на непредвиденные обстоятельства, возможные ошибки в понимании требований, необходимость переделок и консультаций. Оценка должна это отражать. " +
                        "Не забывай, что оценка должна покрывать ВЕСЬ цикл работы: от момента получения задания и первого прочтения до полной сдачи готовой работы, включая все итерации правок и доработок, если таковые потребуются для достижения высокой оценки. " +
                        "Твоя цель – дать максимально безопасную и реалистичную оценку, которая поможет студенту реально спланировать свое время, а не создать ложное впечатление, что все можно сделать очень быстро. Лучше немного переоценить, чем сильно недооценить.";

                    Map<String, Object> shortenedPart = new HashMap<>();
                    shortenedPart.put("text", shortenedPrompt);
                    Map<String, Object> shortenedContent = new HashMap<>();
                    shortenedContent.put("parts", List.of(shortenedPart));
                    
                    // Обновляем только 'contents' в существующем requestBody для повторного запроса
                    Map<String, Object> newRequestBodyForShortened = new HashMap<>(requestBody); // Копируем исходный requestBody
                    newRequestBodyForShortened.put("contents", List.of(shortenedContent)); 

                    HttpEntity<Map<String, Object>> shortenedEntity = new HttpEntity<>(newRequestBodyForShortened, headers);
                    log.info("Повторная попытка Gemini API с сокращенным контекстом размером {} символов", shortenedContextText.length());
                    ResponseEntity<String> shortenedResponse = restTemplate.postForEntity(apiUrl, shortenedEntity, String.class);

                    if (shortenedResponse.getStatusCode() == HttpStatus.OK) {
                        String responseBodyShort = shortenedResponse.getBody();
                        log.debug("Получен ответ от Gemini API (сокращенный): {}", responseBodyShort);
                        JsonNode rootNodeShort = objectMapper.readTree(responseBodyShort);
                        JsonNode candidatesNodeShort = rootNodeShort.path("candidates");
                         if (candidatesNodeShort.isMissingNode() || !candidatesNodeShort.isArray() || candidatesNodeShort.isEmpty()) {
                             throw new RuntimeException("Gemini API (сокращенный) вернул неверный формат ответа (нет candidates).");
                         }
                        String jsonResponseTextShort = candidatesNodeShort.get(0).path("content").path("parts").get(0).path("text").asText();
                         if (jsonResponseTextShort.isEmpty()) {
                            throw new RuntimeException("Gemini API (сокращенный) вернул пустой ответ в parts.text.");
                        }
                        OpenRouterResponse geminiJsonResponseShort = parseOpenRouterResponse(jsonResponseTextShort);
                        log.info("Gemini API (сокращенный) оценил время '{}' в {} минут", taskName, geminiJsonResponseShort.getEstimatedMinutes());
                        return geminiJsonResponseShort;
                    } else {
                        log.error("Ошибка при запросе к Gemini API (сокращенный): {}. Ответ: {}", shortenedResponse.getStatusCode(), shortenedResponse.getBody());
                        throw new RuntimeException("Ошибка при запросе к Gemini API (сокращенный): " + shortenedResponse.getStatusCode() + " " + shortenedResponse.getBody());
                    }
                }
                log.error("Ошибка при обработке запроса к Gemini API: {}", e.getMessage(), e);
                throw e; 
            }
        } catch (Exception e) {
            log.error("Не удалось получить оценку от Gemini API: {}", e.getMessage(), e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Неизвестная ошибка";
            return new OpenRouterResponse(120, "Примерная оценка по умолчанию (Gemini): задание средней сложности, требующее около 2 часов. Точная оценка не удалась из-за ошибки: " + errorMessage.substring(0, Math.min(100, errorMessage.length())));
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

            if (estimatedMinutes == 0 && explanation.isEmpty()) { // Проверка на пустой валидный JSON
                 log.warn("Gemini API вернул пустой, но валидный JSON: {}", jsonContent);
                 throw new RuntimeException("API вернул пустой JSON ответ.");
            }
            return new OpenRouterResponse(estimatedMinutes, explanation);
        } catch (Exception e) {
            log.error("Ошибка при парсинге ответа от API (ожидался JSON): {}. Ответ: '{}'", e.getMessage(), content.substring(0, Math.min(500, content.length())));
            // Попытка извлечь минуты из текста, если парсинг JSON не удался
            int estimatedMinutes = extractMinutesFromText(content); 
            return new OpenRouterResponse(estimatedMinutes, "Не удалось распарсить JSON из ответа API. Извлечено из текста: " + content.substring(0, Math.min(200, content.length())));
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

    @Getter
    public static class OpenRouterResponse {
        private final int estimatedMinutes;
        private final String explanation;

        public OpenRouterResponse(int estimatedMinutes, String explanation) {
            this.estimatedMinutes = estimatedMinutes;
            this.explanation = explanation;
        }
    }


    public List<TaskTimeEstimateResponse> analyzeTasksBySemester(Date date, Long userId) {
        LocalDate localDate = new java.sql.Date(date.getTime()).toLocalDate();
        java.sql.Date semesterDate = determineSemesterDate(localDate);

        log.info("Определен семестр с датой {} для запрошенной даты {}", semesterDate, date);

        List<Task> tasks = findTasksForUserBySemesterWithSource(userId, semesterDate);

        log.info("Найдено {} заданий для пользователя {} в семестре с датой {}", tasks.size(), userId, semesterDate);

        List<TaskTimeEstimateResponse> responses = new ArrayList<>();

        for (Task task : tasks) {
            try {
                if (task.getEstimatedMinutes() != null && task.getTimeEstimateExplanation() != null) {
                    log.info("Найдена существующая оценка времени для задания с ID: {}, оценка: {} минут",
                            task.getId(), task.getEstimatedMinutes());

                    responses.add(TaskTimeEstimateResponse.builder()
                            .taskId(task.getId())
                            .taskName(task.getName())
                            .estimatedMinutes(task.getEstimatedMinutes())
                            .explanation(task.getTimeEstimateExplanation())
                            .createdAt(task.getTimeEstimateCreatedAt())
                            .fromCache(true)
                            .build());
                    continue;
                }

                log.info("Оценка времени не найдена для задания {}, отправляем на анализ в OpenRouter API", task.getId());
                String context = getTaskContext(task, userId);
                OpenRouterResponse geminiResponse = askGeminiForTimeEstimate(context, task.getName());

                task.setEstimatedMinutes(geminiResponse.getEstimatedMinutes());
                task.setTimeEstimateExplanation(geminiResponse.getExplanation());
                task.setTimeEstimateCreatedAt(new Date());
                taskRepository.save(task);

                responses.add(TaskTimeEstimateResponse.builder()
                        .taskId(task.getId())
                        .taskName(task.getName())
                        .estimatedMinutes(geminiResponse.getEstimatedMinutes())
                        .explanation(geminiResponse.getExplanation())
                        .createdAt(task.getTimeEstimateCreatedAt())
                        .fromCache(false)
                        .build());

            } catch (Exception e) {
                log.error("Ошибка при анализе задания {}: {}", task.getId(), e.getMessage(), e);
            }
        }

        return responses;
    }

    public java.sql.Date determineSemesterDate(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();

        if (month >= 9) {
            return java.sql.Date.valueOf(LocalDate.of(year, 9, 1));
        } else if (month <= 6) {
            return java.sql.Date.valueOf(LocalDate.of(year, 2, 1));
        } else {
            return java.sql.Date.valueOf(LocalDate.of(year, 2, 1));
        }
    }


    public List<Task> findTasksForUserBySemesterWithSource(Long userId, java.sql.Date semesterDate) {
        List<Task> tasks = taskRepository.findTasksBySourceAndPersonIdAndSemesterDate(TaskSource.PARSED, userId, semesterDate);

        log.info("Найдено заданий для пользователя {} с источником {} в семестре {}: {}",
                userId, TaskSource.PARSED, semesterDate, tasks.size());

        if (!tasks.isEmpty()) {
            log.info("Найденные задания: {}",
                    tasks.stream().map(Task::getName).collect(Collectors.joining(", ")));
        }

        return tasks;
    }

    public List<TaskTimeEstimateResponse> refreshTaskEstimatesBySemester(Date date, Long userId) {
        LocalDate localDate = new java.sql.Date(date.getTime()).toLocalDate();
        java.sql.Date semesterDate = determineSemesterDate(localDate);

        log.info("Принудительное обновление оценок для семестра с датой {} для пользователя {}", semesterDate, userId);

        List<Task> tasks = findTasksForUserBySemesterWithSource(userId, semesterDate);
        log.info("Найдено {} заданий для принудительного обновления в семестре с датой {}", tasks.size(), semesterDate);

        List<TaskTimeEstimateResponse> responses = new ArrayList<>();

        for (Task task : tasks) {
            try {
                log.info("Принудительное обновление оценки для задания {}, ID: {}", task.getName(), task.getId());
                String context = getTaskContext(task, userId);
                OpenRouterResponse geminiResponse = askGeminiForTimeEstimate(context, task.getName());

                task.setEstimatedMinutes(geminiResponse.getEstimatedMinutes());
                task.setTimeEstimateExplanation(geminiResponse.getExplanation());
                task.setTimeEstimateCreatedAt(new Date());
                taskRepository.save(task);

                responses.add(TaskTimeEstimateResponse.builder()
                        .taskId(task.getId())
                        .taskName(task.getName())
                        .estimatedMinutes(geminiResponse.getEstimatedMinutes())
                        .explanation(geminiResponse.getExplanation())
                        .createdAt(task.getTimeEstimateCreatedAt())
                        .fromCache(false) // Всегда false, так как это принудительное обновление
                        .build());

            } catch (Exception e) {
                log.error("Ошибка при принудительном обновлении задания {}: {}", task.getId(), e.getMessage(), e);
                // Можно добавить обработку ошибки, например, вернуть старую оценку или специальный объект ошибки
                responses.add(TaskTimeEstimateResponse.builder()
                        .taskId(task.getId())
                        .taskName(task.getName() + " (ошибка обновления)")
                        .estimatedMinutes(task.getEstimatedMinutes() != null ? task.getEstimatedMinutes() : 0)
                        .explanation("Не удалось обновить оценку: " + e.getMessage().substring(0, Math.min(100, e.getMessage().length())))
                        .createdAt(task.getTimeEstimateCreatedAt() != null ? task.getTimeEstimateCreatedAt() : new Date())
                        .fromCache(true) // Указываем, что это старая оценка из-за ошибки
                        .build());
            }
        }
        log.info("Завершено принудительное обновление {} оценок для семестра.", responses.size());
        return responses;
    }

    // Новый метод для анализа семестра
    public String getSemesterAnalysis(String semesterAnalysisPrompt) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            log.info("Отправляем запрос Gemini API для анализа семестра с промптом размером {} символов", semesterAnalysisPrompt.length());

            Map<String, Object> part = new HashMap<>();
            part.put("text", semesterAnalysisPrompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(part));

            Map<String, Object> generationConfig = new HashMap<>();
            // generationConfig.put("temperature", 0.2); // Можно настроить для более творческих/менее детерминированных ответов
            generationConfig.put("responseMimeType", "text/plain"); // Ожидаем простой текст

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(content));
            requestBody.put("generationConfig", generationConfig);

            String apiUrl = String.format("%s/%s:generateContent?key=%s", geminiApiBaseUrl, geminiModelName, geminiApiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                log.debug("Получен ответ от Gemini API для анализа семестра: {}", responseBody);
                JsonNode rootNode = objectMapper.readTree(responseBody);

                JsonNode candidatesNode = rootNode.path("candidates");
                if (candidatesNode.isMissingNode() || !candidatesNode.isArray() || candidatesNode.isEmpty()) {
                    log.error("Gemini API (анализ семестра) вернул ответ без валидного поля 'candidates': {}", responseBody);
                    // ... (обработка ошибки, если promptFeedback есть)
                    throw new RuntimeException("Gemini API (анализ семестра) вернул неверный формат ответа (отсутствует 'candidates').");
                }

                String analysisText = candidatesNode.get(0).path("content").path("parts").get(0).path("text").asText();
                if (analysisText.isEmpty()) {
                     log.error("Gemini API (анализ семестра) вернул пустой текст в content.parts[0].text: {}", responseBody);
                     throw new RuntimeException("Gemini API (анализ семестра) вернул пустой ответ в ожидаемом поле.");
                }
                log.info("Gemini API успешно вернул анализ семестра.");
                return analysisText;
            } else {
                log.error("Ошибка при запросе к Gemini API (анализ семестра): {}. Ответ: {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("Ошибка при запросе к Gemini API (анализ семестра): " + response.getStatusCode() + " " + response.getBody());
            }
        } catch (Exception e) {
            log.error("Исключение при вызове Gemini API для анализа семестра: {}", e.getMessage(), e);
            // Можно добавить более специфичную обработку ошибок, если необходимо
            throw new RuntimeException("Ошибка при обращении к сервису анализа семестра: " + e.getMessage(), e);
        }
    }

    // Новый метод для получения задач со статусами
    public List<TaskForStudyPlanDto> findTasksWithStatusForStudyPlan(Long userId, java.sql.Date semesterDate) {
        List<Task> tasks = findTasksForUserBySemesterWithSource(userId, semesterDate); 
        List<TaskForStudyPlanDto> taskDtos = new ArrayList<>();

        for (Task task : tasks) {
            String status = "Не сдано"; // Статус по умолчанию
            StudentTaskAssignment assignment = studentTaskAssignmentRepository
                    .findByTask_IdAndPerson_Id(task.getId(), userId)
                    .orElse(null);

            if (assignment != null) {
                TaskGrading grading = taskGradingRepository.findByAssignment(assignment);
                if (grading != null) {
                    String gStatusLower = (grading.getGradingStatus() != null) ? grading.getGradingStatus().toLowerCase() : "";
                    String subStatusLower = (grading.getSubmissionStatus() != null) ? grading.getSubmissionStatus().toLowerCase() : "";

                    if (gStatusLower.contains("зачет")) {
                        status = "Зачет";
                    } else if (gStatusLower.contains("оценен") && !gStatusLower.contains("не оценен") && !gStatusLower.contains("не зачтен")) { 
                        status = "Оценено";
                    } else if (!subStatusLower.isEmpty() && !subStatusLower.contains("нет ответа")) {
                        status = "Сдано";
                    }
                    // Иначе остается "Не сдано"
                }
            }

            LocalDateTime deadlineForPlanning = null;
            if (task.getDeadline() != null) {
                deadlineForPlanning = LocalDateTime.ofInstant(task.getDeadline().toInstant(), ZoneId.systemDefault());
            }

            taskDtos.add(TaskForStudyPlanDto.builder()
                    .id(task.getId())
                    .name(task.getName())
                    .originalDeadline(task.getDeadline()) // Сохраняем оригинальный Date
                    .deadlineForPlanning(deadlineForPlanning)
                    .estimatedMinutes(task.getEstimatedMinutes())
                    .subjectName(task.getSubject() != null ? task.getSubject().getName() : "Без предмета")
                    .status(status)
                    .build());
        }
        return taskDtos;
    }

    public OpenRouterResponse analyzeParsedTaskAndGetEstimate(ParsedTask parsedTask, String subjectName, String initialMoodleSession, Person person) {
        log.info("Начинаем анализ предварительно спарсенного задания '{}' для пользователя {}", parsedTask.name, person.getEmail());
        StringBuilder allText = new StringBuilder();

        if (subjectName != null && !subjectName.isEmpty()) {
            allText.append("===== ПРЕДМЕТ =====\n\n");
            allText.append(subjectName).append("\n\n");
        }

        if (parsedTask.description != null && !parsedTask.description.isEmpty()) {
            allText.append("===== ОПИСАНИЕ ЗАДАНИЯ =====\n\n");
            allText.append(parsedTask.description).append("\n\n");
        }

        if (parsedTask.attachments != null && !parsedTask.attachments.isEmpty()) {
            log.info("Найдено {} прикрепленных файлов в предварительно спарсенном задании", parsedTask.attachments.size());
            String currentMoodleSession = initialMoodleSession;

            for (ParsedAttachment attachment : parsedTask.attachments) {
                log.info("Обрабатываем файл из ParsedTask: {}, URL: {}", attachment.fileName, attachment.fileUrl);
                try {
                    // Получаем/проверяем сессию перед каждым скачиванием, т.к. она могла обновиться
                    String validMoodleSession = moodleAssignmentService.getValidMoodleSession(person);
                    if (validMoodleSession == null) {
                         log.warn("Не удалось получить/обновить Moodle сессию для пользователя {}. Пропускаем файл {}.", person.getEmail(), attachment.fileName);
                         allText.append("===== ФАЙЛ: ").append(attachment.fileName).append(" =====\n\n");
                         allText.append("Не удалось скачать файл: проблема с Moodle сессией.\n\n");
                         continue;
                    }
                    currentMoodleSession = validMoodleSession; // Обновляем текущую сессию на случай, если она изменилась

                    java.io.File tempFile = moodleAssignmentService.downloadFile(attachment.fileUrl, currentMoodleSession, person);
                    if (tempFile == null) {
                        log.warn("Не удалось скачать файл {} для задания {}. Пропускаем.", attachment.fileName, parsedTask.name);
                        allText.append("===== ФАЙЛ: ").append(attachment.fileName).append(" =====\n\n");
                        allText.append("Не удалось скачать файл: возможно, проблема с доступом или URL.\n\n");
                        continue;
                    }

                    String fileContent = textProcessingService.extractTextFromFile(tempFile);
                    log.info("Извлечен текст из {}, размер текста: {} символов", attachment.fileName, fileContent.length());

                    allText.append("===== ФАЙЛ: ").append(attachment.fileName).append(" =====\n\n");
                    allText.append(fileContent).append("\n\n");

                    if (!tempFile.delete()) {
                        log.warn("Не удалось удалить временный файл: {}", tempFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    log.error("Ошибка при обработке файла {} из ParsedTask: {}", attachment.fileName, e.getMessage(), e);
                    allText.append("===== ФАЙЛ: ").append(attachment.fileName).append(" =====\n\n");
                    allText.append("Ошибка при обработке файла: ").append(e.getMessage()).append("\n\n");
                }
            }
        } else {
            log.info("В предварительно спарсенном задании нет прикрепленных файлов");
        }

        if (allText.length() == 0) {
            log.warn("Контекст для задания '{}' оказался пустым. Оценка времени не будет запрошена.", parsedTask.name);
            return new OpenRouterResponse(0, "Описание и файлы задания не содержат текста для анализа.");
        }
        
        log.info("Сформирован контекст для анализа ({} символов) для задания: {}", allText.length(), parsedTask.name);
        return askGeminiForTimeEstimate(allText.toString(), parsedTask.name);
    }
} 