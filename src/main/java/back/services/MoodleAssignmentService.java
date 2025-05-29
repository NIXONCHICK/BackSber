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


    public String getValidMoodleSession(Person person) {
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


    public File downloadFile(String fileUrl, String moodleSession, Person person) throws IOException {
        IOException lastException = null;
        try {
            log.info("Первая попытка скачать файл: {} для пользователя {}", fileUrl, person.getEmail());
            return downloadFileWithSession(fileUrl, moodleSession);
        } catch (IOException e) {
            String errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (errorMessage.contains("redirected too many") ||
                errorMessage.contains("слишком много редиректов") ||
                errorMessage.contains("код: 30") ||
                errorMessage.contains("код: 401") ||
                errorMessage.contains("код: 403") ||
                errorMessage.contains("код: 429") ||
                errorMessage.contains("код: 5")   ||
                errorMessage.contains("forbidden") ||
                errorMessage.contains("unauthorized") ||
                errorMessage.contains("доступ запрещен") ||
                errorMessage.contains("сессия истекла") ||
                errorMessage.contains("требуется авторизация")) {
                log.warn("Ошибка при скачивании файла ({}) с текущей сессией для {}: {}. Попытка обновить сессию.", fileUrl, person.getEmail(), e.getMessage());
                lastException = e;
            } else {
                log.error("Нераспознанная или не требующая обновления сессии ошибка IO при скачивании файла {} для {}: {}", fileUrl, person.getEmail(), e.getMessage(), e);
                throw e;
            }
        }

        if (lastException == null) {
             log.error("Критическая ошибка логики в downloadFile: lastException is null, хотя должна была быть ошибка. URL: {}", fileUrl);
             throw new IOException("Критическая ошибка логики: lastException не установлен после перехваченной ошибки при скачивании файла " + fileUrl);
        }

        log.info("Пробуем скачать файл {} с новой сессией. Пользователь: {}. Ошибка при первой попытке: {}", fileUrl, person.getEmail(), lastException.getMessage());

        String newSession = null;
        try {
            log.info("НАЧИНАЕМ ВЫЗОВ refreshMoodleSession для пользователя: {}", person.getEmail());
            newSession = refreshMoodleSession(person);
            if (newSession == null) {
                log.error("refreshMoodleSession вернул null для пользователя {}. Обновление сессии не удалось.", person.getEmail());
                throw new IOException("Не удалось обновить Moodle сессию (refreshMoodleSession вернул null) для " + person.getEmail() + " при попытке скачать файл " + fileUrl + ". Исходная ошибка скачивания: " + lastException.getMessage(), lastException);
            } else {
                log.info("Успешно получен новый MoodleSession для {} (длина: {}): {}...", person.getEmail(), newSession.length(), newSession.substring(0, Math.min(10, newSession.length())));
            }
        } catch (Exception refreshEx) {          log.error("Критическая ошибка при вызове refreshMoodleSession для пользователя {}: {}", person.getEmail(), refreshEx.getMessage(), refreshEx);
            throw new IOException("Критическая ошибка во время обновления Moodle сессии для " + person.getEmail() + " (" + refreshEx.getMessage() + ") при попытке скачать файл " + fileUrl + ". Исходная ошибка скачивания: " + lastException.getMessage(), refreshEx);
        }

        log.info("Повторная попытка скачать файл {} с только что обновленной сессией для {}", fileUrl, person.getEmail());
        try {
            return downloadFileWithSession(fileUrl, newSession);
        } catch (IOException finalE) {
            log.error("Не удалось скачать файл {} для {} даже ПОСЛЕ успешного ОБНОВЛЕНИЯ сессии. Ошибка при второй попытке: {}. Исходная ошибка (до обновления сессии): {}",
                      fileUrl, person.getEmail(), finalE.getMessage(), lastException.getMessage(), finalE);
            throw new IOException("Не удалось скачать файл " + fileUrl + " для " + person.getEmail() + " даже после обновления сессии. Ошибка при второй попытке: " + finalE.getMessage() + ". Исходная ошибка перед обновлением: " + lastException.getMessage(), finalE);
        }
    }


    private File downloadFileWithSession(String fileUrl, String moodleSession) throws IOException {
        HttpURLConnection conn = null;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        File tempFile = null;
        int redirectCount = 0;
        final int MAX_REDIRECTS = 5;      String currentUrl = fileUrl;

        try {
            while (redirectCount <= MAX_REDIRECTS) {
                log.info("Попытка скачать файл: {}. Попытка редиректа: {}/{}", currentUrl, redirectCount, MAX_REDIRECTS);
                URL url = new URL(currentUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Cookie", "MoodleSession=" + moodleSession);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(false);
                int responseCode = conn.getResponseCode();

                if (responseCode >= 300 && responseCode < 400) {            String location = conn.getHeaderField("Location");
                    if (location == null) {
                        throw new IOException("Редирект с кодом " + responseCode + " но без заголовка Location для URL: " + currentUrl);
                    }
                    log.info("Редирект с {} на {}. URL: {}", responseCode, location, currentUrl);
                    currentUrl = new URL(url, location).toString();       conn.disconnect();
                    redirectCount++;
                    if (redirectCount > MAX_REDIRECTS) {
                        throw new IOException("Слишком много редиректов (" + redirectCount + ") при скачивании файла: " + fileUrl + ". Последний URL: " + currentUrl);
                    }
                    continue;        }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    String errorDetails = "Не удалось скачать файл, код: " + responseCode + " для URL: " + currentUrl;
                    try (InputStream errorStream = conn.getErrorStream()) {
                        if (errorStream != null) {
                            String errorBody = new BufferedReader(new InputStreamReader(errorStream))
                                    .lines().collect(java.util.stream.Collectors.joining("\n"));
                            errorDetails += ". Тело ошибки: " + errorBody;
                        }
                    } catch (Exception e) {
                        log.warn("Не удалось прочитать тело ошибки для URL {}: {}", currentUrl, e.getMessage());
                    }
                    throw new IOException(errorDetails);
                }

                String rawFileName = currentUrl.substring(currentUrl.lastIndexOf('/') + 1);
                String fileName = rawFileName.contains("?") ? rawFileName.substring(0, rawFileName.indexOf("?")) : rawFileName;

                String contentDisposition = conn.getHeaderField("Content-Disposition");
                String actualFileName = fileName;      if (contentDisposition != null) {
                    String prefix = "filename*=UTF-8''";
                    int index = contentDisposition.toLowerCase().indexOf(prefix);
                    if (index > -1) {
                        actualFileName = contentDisposition.substring(index + prefix.length());
                        actualFileName = java.net.URLDecoder.decode(actualFileName, java.nio.charset.StandardCharsets.UTF_8.name());
                    } else {
                        prefix = "filename=\"";                  index = contentDisposition.toLowerCase().indexOf(prefix);
                        if (index > -1) {
                            actualFileName = contentDisposition.substring(index + prefix.length(), contentDisposition.length() - 1);
                        }
                    }
                     actualFileName = actualFileName.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
                }


                tempFile = Files.createTempFile("downloaded_", "_" + actualFileName).toFile();
                inputStream = conn.getInputStream();
                outputStream = new FileOutputStream(tempFile);

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                log.info("Файл {} успешно скачан во временный файл: {}", actualFileName, tempFile.getAbsolutePath());
                return tempFile;       }
            throw new IOException("Превышено максимальное количество редиректов (" + MAX_REDIRECTS + ") для URL: " + fileUrl);

        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.warn("Ошибка при закрытии InputStream: {}", e.getMessage());
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    log.warn("Ошибка при закрытии OutputStream: {}", e.getMessage());
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
} 