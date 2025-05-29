package back.services;

import back.entities.*;
import back.exceptions.SfedAuthenticationException;
import back.repositories.*;
import back.util.SeleniumUtil;
import back.util.SfedLoginResult;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.ZoneId;
import back.dto.TaskTimeEstimateResponse;

@Service
@RequiredArgsConstructor
@Transactional
public class UserParsingService {

  private final PersonRepository personRepository;
  private final SubjectRepository subjectRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final StudentGroupRepository studentGroupRepository;
  private final TaskRepository taskRepository;
  private final StudentTaskAssignmentRepository studentTaskAssignmentRepository;
  private final TaskGradingRepository taskGradingRepository;
  private final PageParsingService pageParsingService;
  private final OpenRouterService openRouterService;

  @Value("${app.encryption.secret-key}")
  private String encryptionSecretKey;

  private String decrypt(String strToDecrypt) {
    try {
      byte[] keyBytes = encryptionSecretKey.getBytes(StandardCharsets.UTF_8);
      SecretKeySpec secretKey = new SecretKeySpec(Arrays.copyOf(keyBytes, 16), "AES");
      Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
      cipher.init(Cipher.DECRYPT_MODE, secretKey);
      return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)));
    } catch (Exception e) {
      System.err.println("Error while decrypting: " + e.toString());
      throw new RuntimeException("Error decrypting password", e);
    }
  }

  public String validateSfedUCredentialsAndGetSession(String email, String password) {
    try {
      SfedLoginResult loginResult = SeleniumUtil.loginAndGetMoodleSession(email, password);
      
      if (!loginResult.isSuccess()) {
        System.err.println("Ошибка при попытке эмуляции входа в SFEDU для email: " + email +
                           "; Код ошибки: " + loginResult.errorCode() + 
                           "; Сообщение: " + loginResult.detailedErrorMessage());
        throw new SfedAuthenticationException(loginResult.errorCode(), loginResult.detailedErrorMessage());
      }
      return loginResult.moodleSession();
    } catch (SfedAuthenticationException e) {
        throw e;
    } catch (Exception e) {
      System.err.println("Неожиданная ошибка при вызове SeleniumUtil для email: " + email + ": " + e.getMessage());
      throw new SfedAuthenticationException("SFEDU_LOGIN_UNEXPECTED_ERROR",
                                          "Неожиданная системная ошибка при проверке учетных данных SFEDU: " + e.getMessage(), e);
    }
  }

  public boolean parseAndUpdateUser(long personId) {
    Optional<Person> personOptional = personRepository.findById(personId);
    if (personOptional.isEmpty()) {
      System.err.println("parseAndUpdateUser: Person с id " + personId + " не найден.");
      return false;
    }
    Person person = personOptional.get();

    String moodleSession = person.getMoodleSession();
    if (moodleSession == null || moodleSession.trim().isEmpty()) {
      System.err.println("parseAndUpdateUser: Отсутствует Moodle сессия для personId: " + personId + ". Парсинг невозможен.");
      return false;
    }

    String myUrl = "https://lms.sfedu.ru/my/";
    Document myDoc;
    try {
      myDoc = pageParsingService.parsePage(myUrl, moodleSession);
      System.out.println("Страница 'My' успешно загружена.");
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }

    Map<String, ParsedSubject> parsedSubjectsMap = new HashMap<>();

    Elements courseLinks = myDoc.select("a[title][href]:has(span.coc-metainfo)");
    for (Element link : courseLinks) {
      String href = link.attr("href").trim();
      String title = link.attr("title").trim();
      Element span = link.selectFirst("span.coc-metainfo");
      String semesterText = (span != null) ? span.text().trim() : null;
      LocalDate semesterDate = convertSemesterTextToDate(semesterText);

      if (semesterDate == null || href.isEmpty()) {
        continue;
      }

      ParsedSubject parsedSubject = new ParsedSubject();
      parsedSubject.assignmentsUrl = href;
      parsedSubject.name = title;
      parsedSubject.semesterDate = semesterDate;

      parsedSubjectsMap.put(href, parsedSubject);
    }

    if (parsedSubjectsMap.isEmpty()) {
      System.out.println("Не найдено ни одного курса для пользователя " + personId);
    }

    String profileUrl = "https://lms.sfedu.ru/user/profile.php";
    String extractedGroupName = null;
    try {
      Document profileDoc = pageParsingService.parsePage(profileUrl, moodleSession);
      System.out.println("Страница профиля успешно загружена.");

      Element h1 = profileDoc.selectFirst("h1.h2");
      if (h1 != null) {
        String fullName = h1.text().trim();
        String[] nameParts = fullName.split("\\s+");
        if (nameParts.length >= 2) {
          person.setSurname(nameParts[0]);
          person.setName(nameParts[1]);
          if (nameParts.length >= 3) {
            person.setPatronymic(nameParts[2]);
          }
        }
      }

      Element groupElement = profileDoc.selectFirst("dl:has(dt:containsOwn(Группа)) dd");
      if (groupElement != null) {
        extractedGroupName = groupElement.text().trim();
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("Ошибка при парсинге страницы профиля: " + e.getMessage());
    }

    if (parsedSubjectsMap.isEmpty()) {
        System.out.println("Не найдено ни одного курса для пользователя " + personId + ". Обновлен только профиль.");
        if (extractedGroupName != null && !extractedGroupName.isBlank()) {
            StudentGroup group = studentGroupRepository.findByName(extractedGroupName).orElse(null);
            if (group == null) {
                group = new StudentGroup();
                group.setName(extractedGroupName);
                group = studentGroupRepository.save(group);
            }
            person.setGroup(group);
        }
        personRepository.save(person);
        System.out.println("Данные профиля сохранены. Курсы отсутствуют.");
        return true;     }

    LocalDate latestSemesterDate = null;
    for (ParsedSubject ps : parsedSubjectsMap.values()) {
        if (ps.semesterDate != null) {
            if (latestSemesterDate == null || ps.semesterDate.isAfter(latestSemesterDate)) {
                latestSemesterDate = ps.semesterDate;
            }
        }
    }

    Map<String, ParsedSubject> latestSemesterSubjectsMap = new HashMap<>();
    if (latestSemesterDate != null) {
        for (Map.Entry<String, ParsedSubject> entry : parsedSubjectsMap.entrySet()) {
            if (entry.getValue().semesterDate != null && entry.getValue().semesterDate.equals(latestSemesterDate)) {
                latestSemesterSubjectsMap.put(entry.getKey(), entry.getValue());
            }
        }
        System.out.println("Будут обработаны только курсы последнего семестра: " + latestSemesterDate);
    } else {
        System.err.println("Не удалось определить дату последнего семестра. Будут обработаны все найденные курсы.");
        latestSemesterSubjectsMap.putAll(parsedSubjectsMap);     }
    

    for (ParsedSubject parsedSubject : latestSemesterSubjectsMap.values()) {
      try {
        Document subjectDoc = pageParsingService.parsePage(parsedSubject.assignmentsUrl, moodleSession);
        System.out.println("Страница предмета \"" + parsedSubject.name + "\" успешно загружена.");

        Elements assignmentLinks = subjectDoc.select("a.aalink.stretched-link");
        for (Element assignmentLink : assignmentLinks) {
          String taskHref = assignmentLink.attr("href").trim();
          if (!taskHref.startsWith("https://lms.sfedu.ru/mod/assign/view.php")) {
            continue;
          }
          String taskName = "";
          Element nameElement = assignmentLink.selectFirst("span.instancename");
          if (nameElement != null) {
            taskName = nameElement.text().trim();
          }

          ParsedTask parsedTask = new ParsedTask();
          parsedTask.assignmentsUrl = taskHref;
          parsedTask.name = taskName;
          parsedSubject.tasks.add(parsedTask);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }


    for (ParsedSubject parsedSubject : latestSemesterSubjectsMap.values()) {
      for (ParsedTask parsedTask : parsedSubject.tasks) {
        try {
          Document doc = pageParsingService.parsePage(parsedTask.assignmentsUrl, moodleSession);
          System.out.println("Страница задания \"" + parsedTask.name + "\" успешно загружена.");

          Element deadlineDiv = doc.select("div.description-inner > div:has(strong:containsOwn(Срок сдачи))").first();
          if (deadlineDiv != null) {
            String text = deadlineDiv.text();
            LocalDateTime dt = parseDateText(text);
            if (dt != null) {
              parsedTask.deadline = Timestamp.valueOf(dt);
            }
          }

          Element descriptionBlock = doc.selectFirst("div.box.py-3.generalbox.boxaligncenter");
          if (descriptionBlock != null) {
            Element noOverflowDiv = descriptionBlock.selectFirst("div.no-overflow");
            if (noOverflowDiv != null) {
              parsedTask.description = noOverflowDiv.text().trim();
            }
            Element filesTree = descriptionBlock.selectFirst("div[id^=assign_files_tree]");
            if (filesTree != null) {
              Elements attachmentLinks = filesTree.select("a[target=_blank]");
              for (Element a : attachmentLinks) {
                String fileUrl = a.attr("href").trim();
                String fileName = a.text().trim();
                String fileExtension = "";
                int lastDotIndex = fileName.lastIndexOf('.');
                if (lastDotIndex != -1 && lastDotIndex < fileName.length() - 1) {
                  fileExtension = fileName.substring(lastDotIndex + 1);
                }
                ParsedAttachment attach = new ParsedAttachment();
                attach.fileUrl = fileUrl;
                attach.fileName = fileName;
                attach.fileExtension = fileExtension;
                parsedTask.attachments.add(attach);
              }
            }
          }

          String submissionStatus = textFromAdjacentTd(doc, "Состояние ответа на задание");
          String gradingStatus = textFromAdjacentTd(doc, "Состояние оценивания");
          String gradeText = textFromAdjacentTd(doc, "Оценка");
          String submissionDateText = textFromAdjacentTd(doc, "Последнее изменение");

          LocalDateTime submissionDate = parseDateText(submissionDateText);
          Float[] marks = parseGrade(gradeText);

          ParsedSubmission submission = new ParsedSubmission();
          submission.personId = personId;
          submission.submissionStatus = submissionStatus;
          submission.gradingStatus = gradingStatus;
          submission.mark = marks[0];
          submission.maxMark = marks[1];
          submission.submissionDate = (submissionDate != null)
              ? Timestamp.valueOf(submissionDate)
              : null;

          parsedTask.submissionForPerson = submission;

          try {
            OpenRouterService.OpenRouterResponse estimateResponse = openRouterService.analyzeParsedTaskAndGetEstimate(
                parsedTask,
                parsedSubject.name,
                moodleSession,
                person
            );
            if (estimateResponse != null) {
                parsedTask.estimatedMinutes = estimateResponse.getEstimatedMinutes();
                parsedTask.timeEstimateExplanation = estimateResponse.getExplanation();
                parsedTask.timeEstimateCreatedAt = new Date();
            }
          } catch (Exception e) {
              System.err.println("Ошибка при получении оценки времени для задания \\\"" + parsedTask.name + "\\\": " + e.getMessage());
              parsedTask.estimatedMinutes = null;
              parsedTask.timeEstimateExplanation = "Не удалось получить оценку времени: " + e.getMessage();
              parsedTask.timeEstimateCreatedAt = new Date();           }

        } catch (Exception e) {
          e.printStackTrace();
           System.err.println("Ошибка при парсинге деталей задания \\\"" + parsedTask.name + "\\\": " + e.getMessage());
        }
      }
    }

    Set<String> subjectUrls = parsedSubjectsMap.keySet();     List<Subject> existingSubjects = subjectRepository.findAllByAssignmentsUrlIn(subjectUrls);
    Map<String, Subject> urlToSubjectMap = new HashMap<>();
    for (Subject subj : existingSubjects) {
      urlToSubjectMap.put(subj.getAssignmentsUrl(), subj);
    }

    List<Enrollment> existingEnrollments = enrollmentRepository.findAllByPersonId(personId);
    Set<String> existingEnrollmentKeys = new HashSet<>();
    for (Enrollment e : existingEnrollments) {
      existingEnrollmentKeys.add(e.getPerson().getId() + "_" + e.getSubject().getId());
    }

    List<Subject> subjectsToSave = new ArrayList<>();
    List<Enrollment> enrollmentsToSave = new ArrayList<>();

    for (ParsedSubject parsedSubject : parsedSubjectsMap.values()) {       Subject subject = urlToSubjectMap.get(parsedSubject.assignmentsUrl);
      if (subject == null) {
        subject = new Subject();
        subject.setAssignmentsUrl(parsedSubject.assignmentsUrl);
        urlToSubjectMap.put(parsedSubject.assignmentsUrl, subject);
      }
      subject.setName(parsedSubject.name);
      subject.setSemesterDate(java.sql.Date.valueOf(parsedSubject.semesterDate));

      subjectsToSave.add(subject);
    }

    subjectRepository.saveAll(subjectsToSave);
    subjectRepository.flush();
    for (Subject subject : subjectsToSave) {
      String enrollmentKey = person.getId() + "_" + subject.getId();
      if (!existingEnrollmentKeys.contains(enrollmentKey)) {
        Enrollment enrollment = new Enrollment();
        EnrollmentId enrollmentId = new EnrollmentId(person.getId(), subject.getId());
        enrollment.setId(enrollmentId);
        enrollment.setPerson(person);
        enrollment.setSubject(subject);
        enrollmentsToSave.add(enrollment);

        existingEnrollmentKeys.add(enrollmentKey);
      }
    }

    enrollmentRepository.saveAll(enrollmentsToSave);

    Set<String> taskUrls = new HashSet<>();
    for (ParsedSubject ps : parsedSubjectsMap.values()) {
      for (ParsedTask pt : ps.tasks) {
        taskUrls.add(pt.assignmentsUrl);
      }
    }
    List<Task> existingTasks = taskRepository.findAllByAssignmentsUrlIn(taskUrls);
    Map<String, Task> urlToTaskMap = new HashMap<>();
    for (Task t : existingTasks) {
      urlToTaskMap.put(t.getAssignmentsUrl(), t);
    }

    List<Task> tasksToSave = new ArrayList<>();
    List<StudentTaskAssignment> assignmentsToSave = new ArrayList<>();

    for (ParsedSubject ps : parsedSubjectsMap.values()) {
      Subject realSubject = urlToSubjectMap.get(ps.assignmentsUrl);
      if (realSubject == null) {
        System.err.println("Критическая ошибка: Предмет с URL " + ps.assignmentsUrl + " не найден после сохранения.");
        continue;
      }

      for (ParsedTask pt : ps.tasks) {
        Task task = urlToTaskMap.get(pt.assignmentsUrl);
        if (task == null) {
          task = new Task();
          task.setAssignmentsUrl(pt.assignmentsUrl);
          urlToTaskMap.put(pt.assignmentsUrl, task);        }
        task.setName(pt.name);
        task.setSubject(realSubject);
        task.setDeadline(pt.deadline);
        task.setDescription(pt.description);
        task.setSource(TaskSource.PARSED);
        task.setEstimatedMinutes(pt.estimatedMinutes);
        task.setTimeEstimateExplanation(pt.timeEstimateExplanation);
        task.setTimeEstimateCreatedAt(pt.timeEstimateCreatedAt);

        if (task.getAttachments() == null) {
          task.setAttachments(new ArrayList<>());
        } else {
          task.getAttachments().clear();
        }
        for (ParsedAttachment pa : pt.attachments) {
          TaskAttachment attachment = new TaskAttachment();
          attachment.setTask(task);
          attachment.setFileUrl(pa.fileUrl);
          attachment.setFileName(pa.fileName);
          attachment.setFileExtension(pa.fileExtension);
          task.getAttachments().add(attachment);
        }

        tasksToSave.add(task);
      }
    }

    taskRepository.saveAll(tasksToSave);
    taskRepository.flush();
    
    for (ParsedSubject ps : parsedSubjectsMap.values()) {       for (ParsedTask pt : ps.tasks) {
        Task task = urlToTaskMap.get(pt.assignmentsUrl);
        if (task == null || task.getId() == null) {
          continue;
        }
        
        if (pt.submissionForPerson != null) {
          StudentTaskAssignmentId assignmentId = new StudentTaskAssignmentId(task.getId(), person.getId());
          StudentTaskAssignment assignment = studentTaskAssignmentRepository.findById(assignmentId).orElse(null);
          if (assignment == null) {
            assignment = new StudentTaskAssignment();
            assignment.setId(assignmentId);
            assignment.setTask(task);
            assignment.setPerson(person);
          }
          assignmentsToSave.add(assignment);
        }
      }
    }
    
    studentTaskAssignmentRepository.saveAll(assignmentsToSave);
    studentTaskAssignmentRepository.flush();
    
    List<TaskGrading> gradingsToSave = new ArrayList<>();
    
    for (ParsedSubject ps : parsedSubjectsMap.values()) {       for (ParsedTask pt : ps.tasks) {
        Task task = urlToTaskMap.get(pt.assignmentsUrl);
        if (task == null || task.getId() == null) {
          continue;
        }
        
        if (pt.submissionForPerson != null) {
          ParsedSubmission sub = pt.submissionForPerson;
          
          StudentTaskAssignmentId assignmentId = new StudentTaskAssignmentId(task.getId(), person.getId());
          StudentTaskAssignment assignment = studentTaskAssignmentRepository.findById(assignmentId).orElse(null);
          
          if (assignment != null) {
            TaskGrading grading = taskGradingRepository.findByAssignment(assignment);
            if (grading == null) {
              grading = new TaskGrading();
              grading.setAssignment(assignment);
            }
            
            grading.setSubmissionStatus(sub.submissionStatus);
            grading.setGradingStatus(sub.gradingStatus);
            grading.setSubmissionDate(sub.submissionDate);
            grading.setMark(sub.mark);
            grading.setMaxMark(sub.maxMark);
            
            gradingsToSave.add(grading);
          }
        }
      }
    }
    
    taskGradingRepository.saveAll(gradingsToSave);

    if (extractedGroupName != null && !extractedGroupName.isBlank()) {
      StudentGroup group = studentGroupRepository.findByName(extractedGroupName).orElse(null);
      if (group == null) {
        group = new StudentGroup();
        group.setName(extractedGroupName);
        group = studentGroupRepository.save(group);
      }
      person.setGroup(group);
    }

    personRepository.save(person);

    System.out.println("Все данные успешно собраны и сохранены одним набором запросов!");
    return true;
  }


  private String textFromAdjacentTd(Document doc, String label) {
    Element th = doc.selectFirst("th:containsOwn(" + label + ")");
    if (th != null) {
      Element td = th.parent().selectFirst("td");
      if (td != null) {
        return td.text().trim();
      }
    }
    return "";
  }


  private Float[] parseGrade(String text) {
    if (text == null || text.trim().isEmpty() || text.equals("-")) {
      return new Float[]{null, null};
    }
    text = text.replace(',', '.').trim();
    Pattern p = Pattern.compile("(\\d+(\\.\\d+)?)\\s*/\\s*(\\d+(\\.\\d+)?)");
    Matcher m = p.matcher(text);
    if (m.find()) {
      try {
        float val = Float.parseFloat(m.group(1));
        float max = Float.parseFloat(m.group(3));
        return new Float[]{val, max};
      } catch (NumberFormatException e) {
        e.printStackTrace();
      }
    }
    return new Float[]{null, null};
  }


  private LocalDateTime parseDateText(String text) {
    if (text == null) return null;
    Pattern pattern = Pattern.compile("(\\d{1,2}\\s+[а-яА-Я]+\\s+\\d{4},\\s+\\d{2}:\\d{2})");
    Matcher matcher = pattern.matcher(text);
    if (matcher.find()) {
      String dateStr = matcher.group(1);
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", new Locale("ru"));
      try {
        return LocalDateTime.parse(dateStr, formatter);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return null;
  }


  private LocalDate convertSemesterTextToDate(String semesterText) {
    if (semesterText == null || semesterText.isEmpty()) {
      return null;
    }
    semesterText = semesterText.replace("(", "").replace(")", "").trim();
    String[] parts = semesterText.split("\\s+");
    if (parts.length < 2) {
      return null;
    }
    try {
      int year = Integer.parseInt(parts[0]);
      String sem = parts[1].toLowerCase();
      if (sem.contains("осен")) {
        return LocalDate.of(year, 9, 1);
      } else if (sem.contains("весен")) {
        return LocalDate.of(year, 2, 1);
      }
    } catch (NumberFormatException e) {
      e.printStackTrace();
    }
    return null;
  }


  public static class ParsedSubject {
    String assignmentsUrl;
    String name;
    LocalDate semesterDate;
    List<ParsedTask> tasks = new ArrayList<>();
  }


  public static class ParsedTask {
    String assignmentsUrl;
    String name;
    Timestamp deadline;
    String description;
    List<ParsedAttachment> attachments = new ArrayList<>();
    ParsedSubmission submissionForPerson;
    Integer estimatedMinutes;
    String timeEstimateExplanation;
    Date timeEstimateCreatedAt;
  }


  public static class ParsedAttachment {
    String fileUrl;
    String fileName;
    String fileExtension;
  }


  public static class ParsedSubmission {
    Long personId;
    String submissionStatus;
    String gradingStatus;
    Float mark;
    Float maxMark;
    Timestamp submissionDate;
  }

  public List<TaskTimeEstimateResponse> refreshAndAnalyzeSemesterTasks(long personId, LocalDate semesterKeyDate) {
    System.out.println("Запуск refreshAndAnalyzeSemesterTasks для пользователя " + personId + " и семестра с ключевой датой: " + semesterKeyDate);
    List<TaskTimeEstimateResponse> finalResponses = new ArrayList<>();

    Optional<Person> personOptional = personRepository.findById(personId);
    if (personOptional.isEmpty()) {
      System.err.println("refreshAndAnalyzeSemesterTasks: Person с id " + personId + " не найден.");
      return finalResponses;
    }
    Person person = personOptional.get();

    String moodleSession = person.getMoodleSession();
    if (moodleSession == null || moodleSession.trim().isEmpty()) {
      System.err.println("refreshAndAnalyzeSemesterTasks: Отсутствует Moodle сессия для personId: " + personId + ". Попытка первоначального получения сессии.");
      if (person.getEmail() == null || person.getPassword() == null) {
          System.err.println("Невозможно получить сессию: отсутствуют email или зашифрованный пароль для personId: " + person.getId());
          return finalResponses;
      }
      try {
          String encryptedPassword = person.getPassword();
          String decryptedPassword = decrypt(encryptedPassword);
          moodleSession = validateSfedUCredentialsAndGetSession(person.getEmail(), decryptedPassword);
          if (moodleSession != null && !moodleSession.trim().isEmpty()) {
              person.setMoodleSession(moodleSession);
              personRepository.save(person);
              System.out.println("Первоначальная сессия успешно получена для пользователя: " + person.getEmail());
          } else {
              System.err.println("Не удалось получить первоначальную сессию для пользователя: " + person.getEmail());
              return finalResponses;
          }
      } catch (Exception e) {
          System.err.println("Ошибка при получении первоначальной сессии для " + person.getEmail() + ": " + e.getMessage());
          return finalResponses;
      }
    }

    String myUrl = "https://lms.sfedu.ru/my/";
    Document myDoc;
    try {
      myDoc = parsePageWithRefreshLogic(person, myUrl, moodleSession);
      System.out.println("Страница 'My' успешно загружена для определения курсов семестра.");
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("refreshAndAnalyzeSemesterTasks: Ошибка загрузки страницы 'My': " + e.getMessage());
      return finalResponses;
    }

    Map<String, ParsedSubject> targetSemesterParsedSubjectsMap = new HashMap<>();
    Elements courseLinks = myDoc.select("a[title][href]:has(span.coc-metainfo)");

    for (Element link : courseLinks) {
      String href = link.attr("href").trim();
      String title = link.attr("title").trim();
      Element span = link.selectFirst("span.coc-metainfo");
      String semesterText = (span != null) ? span.text().trim() : null;
      LocalDate parsedSemesterDate = convertSemesterTextToDate(semesterText);

      if (parsedSemesterDate == null || href.isEmpty()) {
        continue;
      }
      
      if (parsedSemesterDate.equals(semesterKeyDate)) {
        ParsedSubject parsedSubject = new ParsedSubject();
        parsedSubject.assignmentsUrl = href;
        parsedSubject.name = title;
        parsedSubject.semesterDate = parsedSemesterDate;
        targetSemesterParsedSubjectsMap.put(href, parsedSubject);
      }
    }
    
    if (targetSemesterParsedSubjectsMap.isEmpty()) {
        System.out.println("Не найдено курсов на Moodle для семестра: " + semesterKeyDate);
        return finalResponses;
    }
    System.out.println("Найдено " + targetSemesterParsedSubjectsMap.size() + " курсов для семестра " + semesterKeyDate + " на Moodle.");

    List<ParsedTask> allParsedTasksFromSemester = new ArrayList<>();
    Map<ParsedTask, ParsedSubject> taskToSubjectMap = new HashMap<>();
    for (ParsedSubject parsedSubject : targetSemesterParsedSubjectsMap.values()) {
      try {
        Document subjectDoc = parsePageWithRefreshLogic(person, parsedSubject.assignmentsUrl, person.getMoodleSession());
        System.out.println("Страница предмета \"" + parsedSubject.name + "\" успешно загружена (обновление семестра).");

        Elements assignmentLinks = subjectDoc.select("a.aalink.stretched-link");
        for (Element assignmentLink : assignmentLinks) {
          String taskHref = assignmentLink.attr("href").trim();
          if (!taskHref.startsWith("https://lms.sfedu.ru/mod/assign/view.php")) {
            continue;
          }
          String taskName = "";
          Element nameElement = assignmentLink.selectFirst("span.instancename");
          if (nameElement != null) {
            taskName = nameElement.text().trim();
          }

          ParsedTask parsedTask = new ParsedTask();
          parsedTask.assignmentsUrl = taskHref;
          parsedTask.name = taskName;
          
          try {
            Document taskDoc = parsePageWithRefreshLogic(person, parsedTask.assignmentsUrl, person.getMoodleSession());
            System.out.println("  Детальный парсинг задания \"" + parsedTask.name + "\"...");

            Element deadlineDiv = taskDoc.select("div.description-inner > div:has(strong:containsOwn(Срок сдачи))").first();
            if (deadlineDiv != null) {
              LocalDateTime dt = parseDateText(deadlineDiv.text());
              if (dt != null) parsedTask.deadline = Timestamp.valueOf(dt);
            }

            Element descriptionBlock = taskDoc.selectFirst("div.box.py-3.generalbox.boxaligncenter");
            if (descriptionBlock != null) {
              Element noOverflowDiv = descriptionBlock.selectFirst("div.no-overflow");
              if (noOverflowDiv != null) parsedTask.description = noOverflowDiv.text().trim();
              
              Element filesTree = descriptionBlock.selectFirst("div[id^=assign_files_tree]");
              if (filesTree != null) {
                Elements attachmentElements = filesTree.select("a[target=_blank]");
                for (Element a : attachmentElements) {
                  ParsedAttachment attach = new ParsedAttachment();
                  attach.fileUrl = a.attr("href").trim();
                  attach.fileName = a.text().trim();
                  int lastDotIndex = attach.fileName.lastIndexOf('.');
                  if (lastDotIndex != -1 && lastDotIndex < attach.fileName.length() - 1) {
                    attach.fileExtension = attach.fileName.substring(lastDotIndex + 1);
                  }
                  parsedTask.attachments.add(attach);
                }
              }
            }
            String submissionStatus = textFromAdjacentTd(taskDoc, "Состояние ответа на задание");
            String gradingStatus = textFromAdjacentTd(taskDoc, "Состояние оценивания");
            String gradeText = textFromAdjacentTd(taskDoc, "Оценка");
            String submissionDateText = textFromAdjacentTd(taskDoc, "Последнее изменение");
            LocalDateTime submissionDate = parseDateText(submissionDateText);
            Float[] marks = parseGrade(gradeText);

            ParsedSubmission submission = new ParsedSubmission();
            submission.personId = personId;
            submission.submissionStatus = submissionStatus;
            submission.gradingStatus = gradingStatus;
            submission.mark = marks[0];
            submission.maxMark = marks[1];
            submission.submissionDate = (submissionDate != null) ? Timestamp.valueOf(submissionDate) : null;
            parsedTask.submissionForPerson = submission;

            allParsedTasksFromSemester.add(parsedTask);
            taskToSubjectMap.put(parsedTask, parsedSubject);

          } catch (Exception taskDetailException) {
            taskDetailException.printStackTrace();
            System.err.println("refreshAndAnalyzeSemesterTasks: Ошибка детального парсинга задания \"" + parsedTask.name + "\" (URL: " + parsedTask.assignmentsUrl + "): " + taskDetailException.getMessage());
          }
        }
      } catch (Exception subjectPageException) {
        subjectPageException.printStackTrace();
        System.err.println("refreshAndAnalyzeSemesterTasks: Ошибка парсинга страницы предмета \"" + parsedSubject.name + "\": " + subjectPageException.getMessage());
      }
    }
    System.out.println("Спарсено " + allParsedTasksFromSemester.size() + " задач с их деталями из Moodle для семестра " + semesterKeyDate);

    java.sql.Date semesterSqlDate = java.sql.Date.valueOf(semesterKeyDate);
    List<Task> dbTasksForSemesterList = taskRepository.findTasksBySourceAndPersonIdAndSemesterDate(TaskSource.PARSED, personId, semesterSqlDate);
    
    Map<String, Task> dbTasksMap = new HashMap<>();
    for (Task dbTask : dbTasksForSemesterList) {
        if (dbTask.getAssignmentsUrl() != null) {
            dbTasksMap.put(dbTask.getAssignmentsUrl(), dbTask);
        }
    }
    System.out.println("Найдено " + dbTasksMap.size() + " задач в БД для пользователя " + personId + " и семестра " + semesterKeyDate);

    List<Subject> subjectsToSaveOrUpdate = new ArrayList<>();
    Map<String, Subject> urlToSubjectEntityMap = new HashMap<>();
    
    Set<String> subjectUrlsFromMoodle = targetSemesterParsedSubjectsMap.keySet();
    List<Subject> existingDbSubjects = subjectRepository.findAllByAssignmentsUrlIn(subjectUrlsFromMoodle);
    for(Subject s : existingDbSubjects) urlToSubjectEntityMap.put(s.getAssignmentsUrl(), s);

    for (ParsedSubject parsedSubject : targetSemesterParsedSubjectsMap.values()) {
        Subject subjectEntity = urlToSubjectEntityMap.get(parsedSubject.assignmentsUrl);
        if (subjectEntity == null) {
            subjectEntity = new Subject();
            subjectEntity.setAssignmentsUrl(parsedSubject.assignmentsUrl);
        }
        subjectEntity.setName(parsedSubject.name);
        subjectEntity.setSemesterDate(java.sql.Date.valueOf(parsedSubject.semesterDate));
        subjectsToSaveOrUpdate.add(subjectEntity);
        urlToSubjectEntityMap.put(parsedSubject.assignmentsUrl, subjectEntity);
    }
    if (!subjectsToSaveOrUpdate.isEmpty()) {
        subjectRepository.saveAll(subjectsToSaveOrUpdate);
        subjectRepository.flush();
        System.out.println("Сохранено/обновлено " + subjectsToSaveOrUpdate.size() + " предметов для семестра.");
        for(Subject s : subjectsToSaveOrUpdate) urlToSubjectEntityMap.put(s.getAssignmentsUrl(),s);
    }

    List<Enrollment> enrollmentsToSave = new ArrayList<>();
    List<Enrollment> existingEnrollmentsForSemester = enrollmentRepository.findAllByPersonIdAndSubject_SemesterDate(personId, semesterSqlDate); // !!! НУЖЕН ЭТОТ МЕТОД В РЕПОЗИТОРИИ !!!
    Set<String> existingEnrollmentKeys = new HashSet<>();
    for (Enrollment e : existingEnrollmentsForSemester) {
        existingEnrollmentKeys.add(e.getPerson().getId() + "_" + e.getSubject().getId());
    }

    for (Subject subjectEntity : urlToSubjectEntityMap.values()) {
        if (subjectEntity.getId() == null) {
            System.err.println("refreshAndAnalyzeSemesterTasks: Предмет " + subjectEntity.getName() + " не имеет ID после сохранения, пропускаем создание зачисления.");
            continue;
        }
        String enrollmentKey = person.getId() + "_" + subjectEntity.getId();
        if (!existingEnrollmentKeys.contains(enrollmentKey)) {
            Enrollment enrollment = new Enrollment();
            enrollment.setId(new EnrollmentId(person.getId(), subjectEntity.getId()));
            enrollment.setPerson(person);
            enrollment.setSubject(subjectEntity);
            enrollmentsToSave.add(enrollment);
        }
    }
    if (!enrollmentsToSave.isEmpty()) {
        enrollmentRepository.saveAll(enrollmentsToSave);
        System.out.println("Создано " + enrollmentsToSave.size() + " новых зачислений на предметы семестра.");
    }

    List<Task> tasksToSaveOrUpdate = new ArrayList<>();
    List<StudentTaskAssignment> assignmentsToSaveOrUpdate = new ArrayList<>();
    List<TaskGrading> gradingsToSaveOrUpdate = new ArrayList<>();

    for (ParsedTask parsedTask : allParsedTasksFromSemester) {
        ParsedSubject parentParsedSubject = taskToSubjectMap.get(parsedTask);
        Subject subjectEntity = urlToSubjectEntityMap.get(parentParsedSubject.assignmentsUrl);
        if (subjectEntity == null || subjectEntity.getId() == null) {
            System.err.println("refreshAndAnalyzeSemesterTasks: Не найден или не сохранен родительский предмет для задачи \"" + parsedTask.name + "\". Пропускаем задачу.");
            continue;
        }

        Task taskEntity = dbTasksMap.get(parsedTask.assignmentsUrl);
        boolean isNewTask = taskEntity == null;

        if (isNewTask) {
            taskEntity = new Task();
            taskEntity.setAssignmentsUrl(parsedTask.assignmentsUrl);
            taskEntity.setSource(TaskSource.PARSED);
        }

        taskEntity.setName(parsedTask.name);
        taskEntity.setDescription(parsedTask.description);
        taskEntity.setDeadline(parsedTask.deadline);
        taskEntity.setSubject(subjectEntity);

        if (taskEntity.getAttachments() == null) taskEntity.setAttachments(new ArrayList<>());
        taskEntity.getAttachments().clear();
        if (parsedTask.attachments != null) {
            for (ParsedAttachment pa : parsedTask.attachments) {
                TaskAttachment attachment = new TaskAttachment();
                attachment.setTask(taskEntity);
                attachment.setFileUrl(pa.fileUrl);
                attachment.setFileName(pa.fileName);
                attachment.setFileExtension(pa.fileExtension);
                taskEntity.getAttachments().add(attachment);
            }
        }
        
        if (taskEntity.getEstimatedMinutes() != null && !isNewTask) {
            System.out.println("  Задача \"" + taskEntity.getName() + "\": используется существующая оценка времени из БД.");
            finalResponses.add(TaskTimeEstimateResponse.builder()
                .taskId(taskEntity.getId())
                .taskName(taskEntity.getName())
                .estimatedMinutes(taskEntity.getEstimatedMinutes())
                .explanation(taskEntity.getTimeEstimateExplanation())
                .createdAt(taskEntity.getTimeEstimateCreatedAt())
                .fromCache(true).build());
        } else {
            System.out.println("  Задача \"" + taskEntity.getName() + "\": требуется анализ нейросетью (новая или без оценки).");
            try {
                OpenRouterService.OpenRouterResponse estimateResponse = 
                    openRouterService.analyzeParsedTaskAndGetEstimate(parsedTask, parentParsedSubject.name, moodleSession, person);
                if (estimateResponse != null) {
                    taskEntity.setEstimatedMinutes(estimateResponse.getEstimatedMinutes());
                    taskEntity.setTimeEstimateExplanation(estimateResponse.getExplanation());
                    taskEntity.setTimeEstimateCreatedAt(new Date());
                }
                 finalResponses.add(TaskTimeEstimateResponse.builder()
                    .taskId(isNewTask ? null : taskEntity.getId())
                    .taskName(taskEntity.getName())
                    .estimatedMinutes(taskEntity.getEstimatedMinutes())
                    .explanation(taskEntity.getTimeEstimateExplanation())
                    .createdAt(taskEntity.getTimeEstimateCreatedAt())
                    .fromCache(false).build());
            } catch (Exception e) {
                System.err.println("refreshAndAnalyzeSemesterTasks: Ошибка анализа нейросетью для задачи \"" + parsedTask.name + "\": " + e.getMessage());
                taskEntity.setTimeEstimateExplanation("Ошибка анализа: " + e.getMessage().substring(0, Math.min(250, e.getMessage().length())));
                 finalResponses.add(TaskTimeEstimateResponse.builder()
                    .taskId(isNewTask ? null : taskEntity.getId()) 
                    .taskName(taskEntity.getName())
                    .explanation(taskEntity.getTimeEstimateExplanation())
                    .fromCache(false).build());
            }
        }
        tasksToSaveOrUpdate.add(taskEntity);
    }

    if (!tasksToSaveOrUpdate.isEmpty()) {
        taskRepository.saveAll(tasksToSaveOrUpdate);
        taskRepository.flush();
        System.out.println("Сохранено/обновлено " + tasksToSaveOrUpdate.size() + " задач.");
        Map<String, Task> finalTasksMap = new HashMap<>();
        for(Task t : tasksToSaveOrUpdate) finalTasksMap.put(t.getAssignmentsUrl(),t);

        for(TaskTimeEstimateResponse resp : finalResponses){
            if(resp.getTaskId() == null){
                for(Task t : finalTasksMap.values()){
                    if(t.getName().equals(resp.getTaskName()) && 
                       ((t.getTimeEstimateExplanation()!=null && t.getTimeEstimateExplanation().equals(resp.getExplanation())) || 
                        (t.getEstimatedMinutes()!=null && t.getEstimatedMinutes().equals(resp.getEstimatedMinutes())) ) ){
                        resp.setTaskId(t.getId());
                        if(resp.getCreatedAt() == null && t.getTimeEstimateCreatedAt() !=null) resp.setCreatedAt(t.getTimeEstimateCreatedAt());
                        if(resp.getEstimatedMinutes() == null && t.getEstimatedMinutes() !=null) resp.setEstimatedMinutes(t.getEstimatedMinutes());
                        break;
                    }
                }
            }
        }
    }
    
    for(Task taskEntity : tasksToSaveOrUpdate) {
        ParsedTask originalParsedTask = null;
        for(ParsedTask pt : allParsedTasksFromSemester){
            if(pt.assignmentsUrl.equals(taskEntity.getAssignmentsUrl())){
                originalParsedTask = pt;
                break;
            }
        }
        if(originalParsedTask == null || originalParsedTask.submissionForPerson == null){
            continue;
        }

        ParsedSubmission sub = originalParsedTask.submissionForPerson;
        StudentTaskAssignmentId assignmentId = new StudentTaskAssignmentId(taskEntity.getId(), person.getId());
        StudentTaskAssignment assignment = studentTaskAssignmentRepository.findById(assignmentId).orElse(null);
        boolean isNewAssignment = assignment == null;
        if (isNewAssignment) {
            assignment = new StudentTaskAssignment();
            assignment.setId(assignmentId);
            assignment.setTask(taskEntity);
            assignment.setPerson(person);
        }
        assignmentsToSaveOrUpdate.add(assignment);

        TaskGrading grading = null;
        if(!isNewAssignment) {
             grading = taskGradingRepository.findByAssignment(assignment);
        }
        if (grading == null) {
            grading = new TaskGrading();
            grading.setAssignment(assignment);
        }
        grading.setSubmissionStatus(sub.submissionStatus);
        grading.setGradingStatus(sub.gradingStatus);
        grading.setSubmissionDate(sub.submissionDate);
        grading.setMark(sub.mark);
        grading.setMaxMark(sub.maxMark);
        gradingsToSaveOrUpdate.add(grading);
    }

    if(!assignmentsToSaveOrUpdate.isEmpty()){
        studentTaskAssignmentRepository.saveAll(assignmentsToSaveOrUpdate);
         System.out.println("Сохранено/обновлено " + assignmentsToSaveOrUpdate.size() + " назначений StudentTaskAssignment.");
    }
    if(!gradingsToSaveOrUpdate.isEmpty()){
        taskGradingRepository.saveAll(gradingsToSaveOrUpdate);
         System.out.println("Сохранено/обновлено " + gradingsToSaveOrUpdate.size() + " оценок TaskGrading.");
    }

    System.out.println("Завершение анализа и сохранения задач для семестра " + semesterKeyDate + ". Всего ответов с оценками времени: " + finalResponses.size());

    try {
        java.sql.Date sqlSemesterKeyDate = java.sql.Date.valueOf(semesterKeyDate);
        List<Subject> subjectsInSemester = subjectRepository.findAllBySemesterDate(sqlSemesterKeyDate); // Предполагаем, что такой метод есть или его нужно добавить
        if (!subjectsInSemester.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (Subject subject : subjectsInSemester) {
                subject.setLastAiRefreshTimestamp(now);
            }
            subjectRepository.saveAll(subjectsInSemester);
            System.out.println("Обновлена метка lastAiRefreshTimestamp для " + subjectsInSemester.size() + " предметов семестра " + semesterKeyDate);
        }
    } catch (Exception e) {
        System.err.println("Ошибка при обновлении lastAiRefreshTimestamp для семестра " + semesterKeyDate + ": " + e.getMessage());
    }

    return finalResponses;
  }

  private Document parsePageWithRefreshLogic(Person person, String url, String initialMoodleSession) throws Exception {
    String currentMoodleSession = initialMoodleSession;
    int maxRetries = 1;
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return pageParsingService.parsePage(url, currentMoodleSession);
      } catch (IOException e) {
        if (e.getMessage() != null && e.getMessage().contains("Too many redirects")) {
          if (attempt < maxRetries) {
            System.out.println("Обнаружена ошибка 'Too many redirects' при доступе к " + url + ". Попытка обновить сессию для пользователя: " + person.getEmail());
            if (person.getEmail() == null || person.getPassword() == null) {
                 System.err.println("Невозможно обновить сессию: отсутствуют email или зашифрованный пароль для personId: " + person.getId());
                 throw e;
            }
            try {
              String encryptedPassword = person.getPassword();
              String decryptedPassword = decrypt(encryptedPassword);
              String newMoodleSession = validateSfedUCredentialsAndGetSession(person.getEmail(), decryptedPassword);
              if (newMoodleSession != null && !newMoodleSession.trim().isEmpty()) {
                person.setMoodleSession(newMoodleSession);
                personRepository.save(person);
                currentMoodleSession = newMoodleSession;
                System.out.println("Сессия успешно обновлена для пользователя: " + person.getEmail() + ". Повторная попытка доступа к " + url);
                continue;
              } else {
                System.err.println("Не удалось обновить сессию (новая сессия пуста) для пользователя: " + person.getEmail());
                throw e;
              }
            } catch (Exception refreshException) {
              System.err.println("Ошибка при попытке обновления сессии для " + person.getEmail() + ": " + refreshException.getMessage());
              throw e;
            }
          } else {
            System.err.println("Достигнуто максимальное количество попыток обновления сессии для " + url);
            throw e;
          }
        } else {
          throw e;
        }
      }
    }
    throw new RuntimeException("Не удалось загрузить страницу " + url + " после попыток обновления сессии.");
  }
}