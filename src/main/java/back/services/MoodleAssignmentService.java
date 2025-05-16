package back.services;

import back.dto.AssignmentAnalysisResult;
import back.entities.Person;
import back.entities.Task;
import back.entities.TaskAttachment;
import back.repositories.PersonRepository;
import back.repositories.TaskRepository;
import back.util.EncryptionUtil;
import back.util.SeleniumUtil;
import back.util.SfedLoginResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
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
    private final EncryptionUtil encryptionUtil;

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
        if (moodleSession == null) {
            log.error("Не удалось получить/обновить Moodle сессию для пользователя {}. Анализ задания {} прерван.", person.getEmail(), assignmentId);
            throw new RuntimeException("Не удалось получить действительную Moodle сессию для анализа задания.");
        }

        try {
            AssignmentAnalysisResult result = AssignmentAnalysisResult.builder()
                    .assignmentName(task.getName())
                    .courseName(task.getSubject() != null ? task.getSubject().getName() : "Неизвестный курс")
                    .build();

            StringBuilder allText = new StringBuilder();
            int totalTokens = 0;

            if (task.getSubject() != null) {
                allText.append("===== ПРЕДМЕТ =====\\n\\n");
                allText.append(task.getSubject().getName()).append("\\n\\n");
            }

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
                        File tempFile = downloadFile(attachment.getFileUrl(), moodleSession, person);
                        if (tempFile == null) {
                            log.warn("Не удалось скачать файл {} для задания {}. Пропускаем.", attachment.getFileName(), assignmentId);
                            result.getFiles().add(new AssignmentAnalysisResult.FileAnalysisDetail(
                                attachment.getFileName(),
                                attachment.getFileExtension(),
                                0,
                                "Не удалось скачать файл: проблема с доступом или сессией SFEDU."
                            ));
                            continue;
                        }

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
                         result.getFiles().add(new AssignmentAnalysisResult.FileAnalysisDetail(
                                attachment.getFileName(),
                                attachment.getFileExtension(),
                                0,
                                "Ошибка при обработке файла: " + e.getMessage()
                        ));
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
                log.warn("Ошибка при проверке существующей MoodleSession для {}: {}. Попытка обновить.", person.getEmail(), e.getMessage());
            }
        } else {
            log.info("MoodleSession отсутствует для пользователя {}. Попытка получить новую.", person.getEmail());
        }

        return refreshMoodleSession(person);
    }


    private String refreshMoodleSession(Person person) {
        log.info("Обновление MoodleSession через Selenium для {}", person.getEmail());
        String decryptedPassword;
        try {
            decryptedPassword = encryptionUtil.decryptPassword(person.getPassword());
        } catch (Exception e) {
            log.error("Не удалось расшифровать пароль для пользователя {}: {}. Обновление сессии невозможно.", person.getEmail(), e.getMessage());
            return null;
        }
        
        SfedLoginResult loginResult = SeleniumUtil.loginAndGetMoodleSession(person.getEmail(), decryptedPassword);
        
        if (!loginResult.isSuccess()) {
            log.error("Не удалось получить новую MoodleSession через Selenium для {}. Код ошибки: {}, Сообщение: {}", 
                      person.getEmail(), loginResult.errorCode(), loginResult.detailedErrorMessage());
            return null; 
        }

        String newSession = loginResult.moodleSession();
        log.info("Получен новый MoodleSession для {}: {}", person.getEmail(), newSession.substring(0, Math.min(10, newSession.length())) + "...");

        person.setMoodleSession(newSession);
        personRepository.save(person);
        return newSession;
    }


    private File downloadFile(String fileUrl, String moodleSession, Person person) throws IOException {
        IOException lastException = null;
        try {
            return downloadFileWithSession(fileUrl, moodleSession);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().matches(".*(код: (30\\d|40[13]|429|5\\d{2})|redirected too many|Forbidden|Unauthorized|доступ запрещен|сессия истекла|требуется авторизация).*i")) {
                log.warn("Возникла ошибка при скачивании файла ({}) с текущей сессией для {}: {}. Попытка обновить сессию.", fileUrl, person.getEmail(), e.getMessage());
                lastException = e;
            } else {
                throw e;
            }
        }

        if (lastException == null) {
             log.error("Непредвиденная ситуация в downloadFile: lastException is null, но мы не вышли по throw e. Исходное сообщение: {}", (fileUrl != null ? fileUrl : "URL не указан"));
             throw new IOException("Непредвиденная ошибка при скачивании файла " + (fileUrl != null ? fileUrl : "(URL не указан)"));
        }
        
        log.info("Пробуем скачать файл {} с новой сессией после ошибки: {}", fileUrl, lastException.getMessage());
        String newSession = refreshMoodleSession(person);
        if (newSession == null) {
            log.error("Не удалось обновить сессию для пользователя {}. Скачивание файла {} отменено.", person.getEmail(), fileUrl);
            return null; 
        }

        try {
            return downloadFileWithSession(fileUrl, newSession);
        } catch (IOException finalE) {
            log.error("Не удалось скачать файл {} для {} даже после обновления сессии: {}", fileUrl, person.getEmail(), finalE.getMessage());
            throw new IOException("Не удалось скачать файл " + fileUrl + " после попытки обновления сессии. Исходная проблема: " + lastException.getMessage(), finalE);
        }
    }


    private File downloadFileWithSession(String fileUrl, String moodleSession) throws IOException {
        try {
            String cleanUrl = fileUrl.contains("?")
                    ? fileUrl.substring(0, fileUrl.indexOf("?"))
                    : fileUrl;

            URL url = new URL(fileUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cookie", "MoodleSession=" + moodleSession);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("Не удалось скачать файл, код: " + responseCode + " для URL: " + fileUrl);
            }

            String originalFileName = cleanUrl.substring(cleanUrl.lastIndexOf("/") + 1);
            String fileExtension = "";
            int lastDot = originalFileName.lastIndexOf('.');
            if (lastDot > 0 && lastDot < originalFileName.length() - 1) {
                fileExtension = originalFileName.substring(lastDot);
            } else {
                fileExtension = ".tmp";
            }
            fileExtension = fileExtension.replaceAll("[^a-zA-Z0-9.]", "").replaceAll("\\.{2,}", ".");
            if (fileExtension.isEmpty() || !fileExtension.startsWith(".")) {
                 fileExtension = ".tmp";
            }

            File tempFile = Files.createTempFile("moodle_dl_", fileExtension).toFile();
            log.info("Скачиваем файл (оригинальное имя: {}), сохраняем во временный файл: {}", originalFileName, tempFile.getAbsolutePath());

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(tempFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            log.info("Файл успешно скачан (оригинальное имя: {}), временный файл: {}, размер: {} байт", originalFileName, tempFile.getName(), tempFile.length());
            return tempFile;
        } catch (Exception e) {
            log.error("Ошибка при скачивании файла {} с сессией: {}", (fileUrl != null ? fileUrl : "(URL не указан)"), e.getMessage());
            throw new IOException("Не удалось скачать файл " + (fileUrl != null ? fileUrl : "(URL не указан)") + ": " + e.getMessage(), e);
        }
    }
} 