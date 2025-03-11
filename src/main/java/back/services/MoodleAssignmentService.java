package back.services;

import back.dto.AssignmentAnalysisResult;
import back.entities.Person;
import back.entities.Task;
import back.entities.TaskAttachment;
import back.repositories.PersonRepository;
import back.repositories.TaskRepository;
import back.util.SeleniumUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MoodleAssignmentService {

    private final PersonRepository personRepository;
    private final TaskRepository taskRepository;
    private final PageParsingService pageParsingService;
    private final TextProcessingService textProcessingService;

    @Value("${moodle.base-url:https://lms.sfedu.ru}")
    private String moodleBaseUrl;


    public AssignmentAnalysisResult analyzeAssignment(Long assignmentId, Long userId) {
        Person person = personRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        log.info("Начинаем анализ задания {} для пользователя {}", assignmentId, person.getEmail());

        Task task = taskRepository.findTaskByIdForUser(assignmentId, userId)
                .orElseThrow(() -> new RuntimeException("Задание не найдено или недоступно для пользователя"));

        log.info("Задание найдено: {}, URL: {}", task.getName(), task.getAssignmentsUrl());

        String moodleSession = getValidMoodleSession(person);

        try {
            AssignmentAnalysisResult result = AssignmentAnalysisResult.builder()
                    .assignmentName(task.getName())
                    .courseName(task.getSubject() != null ? task.getSubject().getName() : "Неизвестный курс")
                    .build();

            StringBuilder allText = new StringBuilder();
            int totalTokens = 0;

            // Если у задания есть описание, добавляем его в анализ
            if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                log.info("Анализируем описание задания");
                int descriptionTokens = textProcessingService.countTokens(task.getDescription());
                result.getFiles().add(new AssignmentAnalysisResult.FileAnalysisDetail(
                        "description.txt",
                        "txt",
                        descriptionTokens,
                        task.getDescription()
                ));

                totalTokens += descriptionTokens;
                allText.append("===== ОПИСАНИЕ ЗАДАНИЯ =====\n\n");
                allText.append(task.getDescription()).append("\n\n");
            }

            if (task.getAttachments() != null && !task.getAttachments().isEmpty()) {
                log.info("Найдено {} прикрепленных файлов", task.getAttachments().size());

                for (TaskAttachment attachment : task.getAttachments()) {
                    log.info("Обрабатываем файл: {}, URL: {}", attachment.getFileName(), attachment.getFileUrl());

                    try {
                        File tempFile = downloadFile(attachment.getFileUrl(), moodleSession);

                        String fileContent = textProcessingService.extractTextFromFile(tempFile);
                        log.info("Извлечен текст из {}, размер текста: {} символов", attachment.getFileName(), fileContent.length());

                        int tokenCount = textProcessingService.countTokens(fileContent);
                        log.info("Подсчитано токенов: {}", tokenCount);
                        totalTokens += tokenCount;

                        result.getFiles().add(new AssignmentAnalysisResult.FileAnalysisDetail(
                                attachment.getFileName(),
                                attachment.getFileExtension(),
                                tokenCount,
                                fileContent
                        ));

                        allText.append("===== ФАЙЛ: ").append(attachment.getFileName()).append(" =====\n\n");
                        allText.append(fileContent).append("\n\n");

                        tempFile.delete();
                    } catch (Exception e) {
                        log.error("Ошибка при обработке файла {}: {}", attachment.getFileName(), e.getMessage(), e);
                    }
                }
            } else {
                log.info("В задании нет прикрепленных файлов");
            }

            result.setExtractedText(allText.toString());
            result.setTotalTokens(totalTokens);

            log.info("Анализ задания завершен, найдено {} токенов", totalTokens);
            return result;
        } catch (Exception e) {log.error("Ошибка при анализе задания: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось проанализировать задание: " + e.getMessage(), e);
        }
    }


    private String getValidMoodleSession(Person person) {
        log.info("Проверка MoodleSession для пользователя {}", person.getEmail());

        if (person.getMoodleSession() != null) {
            try {
                log.info("Тестируем существующую MoodleSession: {}", person.getMoodleSession().substring(0, Math.min(10, person.getMoodleSession().length())) + "...");
                Document doc = pageParsingService.parsePage(moodleBaseUrl, person.getMoodleSession());

                if (doc.selectFirst("div.usermenu") != null) {
                    log.info("MoodleSession действителен");
                    return person.getMoodleSession();
                } else {
                    log.info("MoodleSession недействителен (нет элемента usermenu)");
                }
            } catch (Exception e) {
                log.info("MoodleSession недействителен: {}", e.getMessage());
            }
        } else {
            log.info("MoodleSession отсутствует");
        }

        log.info("Получаем новый MoodleSession через Selenium для {}", person.getEmail());
        String newSession = SeleniumUtil.loginAndGetMoodleSession(person.getEmail(), person.getPassword());
        if (newSession == null) {
            log.error("Не удалось получить MoodleSession через Selenium");
            throw new RuntimeException("Не удалось войти в Moodle");
        }

        log.info("Получен новый MoodleSession: {}", newSession.substring(0, Math.min(10, newSession.length())) + "...");

        person.setMoodleSession(newSession);
        personRepository.save(person);

        return newSession;
    }


    private File downloadFile(String fileUrl, String moodleSession) throws IOException {
        boolean retryWithNewSession = false;
        IOException lastException = null;

        try {
            return downloadFileWithSession(fileUrl, moodleSession);
        } catch (IOException e) {
            // Проверяем, связана ли ошибка с перенаправлениями
            if (e.getMessage().contains("redirected too many") ||
                e.getMessage().contains("код: 30") ||
                e.getMessage().contains("код: 40")) {

                log.warn("Возникла ошибка при скачивании файла с текущей сессией: {}", e.getMessage());
                retryWithNewSession = true;
                lastException = e;
            } else {
                throw e;
            }
        }

        // Если требуется повторная попытка с новой сессией
        if (retryWithNewSession) {
            log.info("Пробуем скачать файл с новой сессией после ошибки: {}", lastException.getMessage());
            // Получаем текущего пользователя
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) auth.getPrincipal();
                Person person = personRepository.findByEmail(userDetails.getUsername());

                if (person == null) {
                    throw new RuntimeException("Пользователь не найден");
                }

                // Принудительно получаем новую сессию
                String newSession = null;
                try {
                    log.info("Принудительно запрашиваем новую сессию для пользователя {}", person.getEmail());
                    newSession = SeleniumUtil.loginAndGetMoodleSession(person.getEmail(), person.getPassword());

                    if (newSession != null) {
                        log.info("Получена новая сессия, сохраняем и повторяем запрос");
                        person.setMoodleSession(newSession);
                        // Сохраняем без установки срока действия сессии, так как поле не используется
                        personRepository.save(person);

                        // Повторяем запрос с новой сессией
                        return downloadFileWithSession(fileUrl, newSession);
                    }
                } catch (Exception seleniumEx) {
                    log.error("Не удалось получить новую сессию: {}", seleniumEx.getMessage());
                    throw new IOException("Не удалось скачать файл после попытки обновления сессии", lastException);
                }
            }

            throw new IOException("Не удалось скачать файл и не удалось обновить сессию", lastException);
        }

        // Этот код не должен быть достижим
        throw new IOException("Неизвестная ошибка при скачивании файла");
    }

    /**
     * Скачивает файл, используя указанную сессию Moodle
     */
    private File downloadFileWithSession(String fileUrl, String moodleSession) throws IOException {
        try {
            // Удаляем параметры из URL для имени файла
            String cleanUrl = fileUrl.contains("?")
                    ? fileUrl.substring(0, fileUrl.indexOf("?"))
                    : fileUrl;

            URL url = new URL(fileUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cookie", "MoodleSession=" + moodleSession);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            // Устанавливаем тайм-аут соединения
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("Не удалось скачать файл, код: " + responseCode);
            }

            String fileName = cleanUrl.substring(cleanUrl.lastIndexOf("/") + 1);
            fileName = fileName.replaceAll("[^a-zA-Z0-9_.-]", "_");

            File tempFile = Files.createTempFile("moodle_", "_" + fileName).toFile();
            log.info("Скачиваем файл из {} в {}", fileUrl, tempFile.getAbsolutePath());

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(tempFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            log.info("Файл успешно скачан: {}, размер: {} байт", fileName, tempFile.length());
            return tempFile;
        } catch (Exception e) {
            log.error("Ошибка при скачивании файла {}: {}", fileUrl, e.getMessage());
            throw new IOException("Не удалось скачать файл: " + e.getMessage(), e);
        }
    }
} 