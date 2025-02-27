package back.services;

import back.dto.LoginRequest;
import back.entities.*;
import back.repositories.*;
import back.util.SeleniumUtil;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional
public class UserParsingService {

  private final PersonRepository personRepository;
  private final SubjectRepository subjectRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final StudentGroupRepository studentGroupRepository;
  private final TaskRepository taskRepository;
  private final TaskSubmissionRepository taskSubmissionRepository;
  private final PageParsingService pageParsingService;

  /**
   * Вся логика в одной транзакции:
   *  - Парсим страницы, собираем данные в память.
   *  - В конце сохраняем все сущности большими партиями (saveAll).
   */
  @Async
  public void parseAndUpdateUser(LoginRequest loginRequest, long personId) {
    // 1) Ищем Person
    Optional<Person> personOptional = personRepository.findById(personId);
    if (personOptional.isEmpty()) {
      return;
    }
    Person person = personOptional.get();

    // 2) Логинимся через Selenium и получаем MoodleSession
    String moodleSession = SeleniumUtil.loginAndGetMoodleSession(
        loginRequest.getEmail(), loginRequest.getPassword()
    );
    // Обновляем в памяти, пока не сохраняем
    person.setMoodleSession(moodleSession);

    // ===========================
    // ЭТАП 1: ПАРСИНГ И СБОР ДАННЫХ
    // ===========================
    // Собираем все предметы, их задания, вложения и статусы в *памяти*.

    // --- 2.1. Парсим страницу "My" и собираем ссылки на курсы
    String myUrl = "https://lms.sfedu.ru/my/";
    Document myDoc;
    try {
      myDoc = pageParsingService.parsePage(myUrl, moodleSession);
      System.out.println("Страница 'My' успешно загружена.");
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }

    // Здесь храним промежуточные данные о предметах, собранные с /my/
    // Ключ: assignmentsUrl, Значение: объект-обёртка ParsedSubject
    Map<String, ParsedSubject> parsedSubjectsMap = new HashMap<>();

    // Собираем ссылки на курсы
    Elements courseLinks = myDoc.select("a[title][href]:has(span.coc-metainfo)");
    for (Element link : courseLinks) {
      String href = link.attr("href").trim();   // ссылка на курс
      String title = link.attr("title").trim(); // название
      Element span = link.selectFirst("span.coc-metainfo");
      String semesterText = (span != null) ? span.text().trim() : null;
      LocalDate semesterDate = convertSemesterTextToDate(semesterText);

      if (semesterDate == null || href.isEmpty()) {
        // Если не смогли распарсить семестр или нет ссылки
        continue;
      }

      ParsedSubject parsedSubject = new ParsedSubject();
      parsedSubject.assignmentsUrl = href;
      parsedSubject.name = title;
      parsedSubject.semesterDate = semesterDate;

      parsedSubjectsMap.put(href, parsedSubject);
    }

    // --- 2.2. Для каждого собранного предмета парсим задания
    for (ParsedSubject parsedSubject : parsedSubjectsMap.values()) {
      try {
        Document subjectDoc = pageParsingService.parsePage(parsedSubject.assignmentsUrl, moodleSession);
        System.out.println("Страница предмета \"" + parsedSubject.name + "\" успешно загружена.");

        // Ищем ссылки на задания
        Elements assignmentLinks = subjectDoc.select("a.aalink.stretched-link");
        for (Element assignmentLink : assignmentLinks) {
          String taskHref = assignmentLink.attr("href").trim();
          // Интересуют только ссылки, начинающиеся на /mod/assign/view.php
          if (!taskHref.startsWith("https://lms.sfedu.ru/mod/assign/view.php")) {
            continue;
          }
          String taskName = "";
          Element nameElement = assignmentLink.selectFirst("span.instancename");
          if (nameElement != null) {
            taskName = nameElement.text().trim();
          }

          // Сохраняем в parsedSubject.listOfTasks
          ParsedTask parsedTask = new ParsedTask();
          parsedTask.assignmentsUrl = taskHref;
          parsedTask.name = taskName;
          parsedSubject.tasks.add(parsedTask);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    // --- 2.3. Парсим детали для каждого задания (дедлайн, описание, вложения, статусы)
    //         и заполняем submissions для текущего person
    for (ParsedSubject parsedSubject : parsedSubjectsMap.values()) {
      for (ParsedTask parsedTask : parsedSubject.tasks) {
        try {
          Document doc = pageParsingService.parsePage(parsedTask.assignmentsUrl, moodleSession);
          System.out.println("Страница задания \"" + parsedTask.name + "\" успешно загружена.");

          // 1) Парсим дедлайн
          Element deadlineDiv = doc.selectFirst("div:has(strong:containsOwn(Срок сдачи))");
          if (deadlineDiv != null) {
            String text = deadlineDiv.text().replace("Срок сдачи:", "").trim();
            LocalDateTime dt = parseDateText(text);
            if (dt != null) {
              parsedTask.deadline = Timestamp.valueOf(dt);
            }
          }

          // 2) Парсим описание и вложения
          Element descriptionBlock = doc.selectFirst("div.box.py-3.generalbox.boxaligncenter");
          if (descriptionBlock != null) {
            // Описание
            Element noOverflowDiv = descriptionBlock.selectFirst("div.no-overflow");
            if (noOverflowDiv != null) {
              parsedTask.description = noOverflowDiv.text().trim();
            }
            // Вложения
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

          // 3) Парсим пользовательские данные (submission)
          String submissionStatus = textFromAdjacentTd(doc, "Состояние ответа на задание");
          String gradingStatus   = textFromAdjacentTd(doc, "Состояние оценивания");
          String gradeText       = textFromAdjacentTd(doc, "Оценка");
          String submissionDateText = textFromAdjacentTd(doc, "Дата отправки");

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

          // Связь с конкретным заданием
          parsedTask.submissionForPerson = submission;

        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    // --- 2.4. Парсим страницу профиля (ФИО, группа)
    String profileUrl = "https://lms.sfedu.ru/user/profile.php";
    String extractedGroupName = null;
    try {
      Document profileDoc = pageParsingService.parsePage(profileUrl, moodleSession);
      System.out.println("Страница профиля успешно загружена.");

      // ФИО
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

      // Группа
      Element groupElement = profileDoc.selectFirst("dl:has(dt:containsOwn(Группа)) dd");
      if (groupElement != null) {
        extractedGroupName = groupElement.text().trim();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    // ===========================
    // ЭТАП 2: МИНИМУМ ЗАПРОСОВ К БД
    // ===========================
    // Собираем всё, что надо сохранить, и делаем "большие" saveAll(...).

    // Список всех ссылок на предметы
    Set<String> subjectUrls = parsedSubjectsMap.keySet();
    // Выгружаем уже существующие Subject разом
    List<Subject> existingSubjects = subjectRepository.findAllByAssignmentsUrlIn(subjectUrls);
    // Кладём в map для быстрого поиска
    Map<String, Subject> urlToSubjectMap = new HashMap<>();
    for (Subject subj : existingSubjects) {
      urlToSubjectMap.put(subj.getAssignmentsUrl(), subj);
    }

    // Для enrollments нужно понять, какие Subject привязаны к Person
    // Сразу выгружаем все его существующие записи Enrollment
    List<Enrollment> existingEnrollments = enrollmentRepository.findAllByPersonId(personId);
    // Для быстрого поиска делаем set из (personId, subjectId)
    Set<String> existingEnrollmentKeys = new HashSet<>();
    for (Enrollment e : existingEnrollments) {
      existingEnrollmentKeys.add(e.getPerson().getId() + "_" + e.getSubject().getId());
    }

    // Сборник Subject, которые надо сохранить (новые или изменённые)
    List<Subject> subjectsToSave = new ArrayList<>();
    // Сборник Enrollment, которые надо сохранить
    List<Enrollment> enrollmentsToSave = new ArrayList<>();

    // --- 2.1. Мержим Subject + создаём Enrollment
    for (ParsedSubject parsedSubject : parsedSubjectsMap.values()) {
      Subject subject = urlToSubjectMap.get(parsedSubject.assignmentsUrl);
      if (subject == null) {
        subject = new Subject();
        subject.setAssignmentsUrl(parsedSubject.assignmentsUrl);
        urlToSubjectMap.put(parsedSubject.assignmentsUrl, subject);
      }
      subject.setName(parsedSubject.name);
      subject.setSemesterDate(java.sql.Date.valueOf(parsedSubject.semesterDate));

      subjectsToSave.add(subject);

      // Enrollment
      // Проверяем, нет ли уже связи person-subject
      // (после сохранения subject получит id, но если он уже в БД, id не null)
      // Однако, чтобы избежать проблемы с "0" id для новых Subject,
      // можно сначала сохранить Subject, потом создать Enrollment.
      // Либо откладываем Enrollment до следующего шага, когда точно будут id.

    }

    // Сохраняем все Subject одним махом (если часть из них новые — они получат id)
    subjectRepository.saveAll(subjectsToSave);
    subjectRepository.flush(); // чтобы у новых Subject появились ID

    // Теперь создаём Enrollment для каждого Subject, если не существует
    for (Subject subject : subjectsToSave) {
      String enrollmentKey = person.getId() + "_" + subject.getId();
      if (!existingEnrollmentKeys.contains(enrollmentKey)) {
        Enrollment enrollment = new Enrollment();
        EnrollmentId enrollmentId = new EnrollmentId(person.getId(), subject.getId());
        enrollment.setId(enrollmentId);
        enrollment.setPerson(person);
        enrollment.setSubject(subject);
        enrollmentsToSave.add(enrollment);

        existingEnrollmentKeys.add(enrollmentKey); // чтобы не дублировать
      }
    }

    // Сохраняем все Enrollment
    enrollmentRepository.saveAll(enrollmentsToSave);

    // --- 2.2. Теперь мержим Task и TaskSubmission
    // Соберём все ссылки на задания
    Set<String> taskUrls = new HashSet<>();
    for (ParsedSubject ps : parsedSubjectsMap.values()) {
      for (ParsedTask pt : ps.tasks) {
        taskUrls.add(pt.assignmentsUrl);
      }
    }
    // Выгружаем существующие Task
    List<Task> existingTasks = taskRepository.findAllByAssignmentsUrlIn(taskUrls);
    Map<String, Task> urlToTaskMap = new HashMap<>();
    for (Task t : existingTasks) {
      urlToTaskMap.put(t.getAssignmentsUrl(), t);
    }

    List<Task> tasksToSave = new ArrayList<>();
    List<TaskSubmission> submissionsToSave = new ArrayList<>();

    // Пробегаемся по всем ParsedTask
    for (ParsedSubject ps : parsedSubjectsMap.values()) {
      // Найдём реальный Subject, чтобы привязать Task к нему
      Subject realSubject = urlToSubjectMap.get(ps.assignmentsUrl);
      if (realSubject == null) {
        // Теоретически не должно случиться, но на всякий случай
        continue;
      }

      for (ParsedTask pt : ps.tasks) {
        Task task = urlToTaskMap.get(pt.assignmentsUrl);
        if (task == null) {
          task = new Task();
          task.setAssignmentsUrl(pt.assignmentsUrl);
          urlToTaskMap.put(pt.assignmentsUrl, task);
        }
        task.setName(pt.name);
        task.setSubject(realSubject);
        task.setDeadline(pt.deadline);
        task.setDescription(pt.description);

        // Добавляем вложения
        // Если у вас @OneToMany(cascade = ALL) на attachments,
        // то можно просто setAttachments(...) и всё сохранится при save(task).
        // Предварительно почистим, чтобы не было старых лишних данных.
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

        // TaskSubmission (данные для конкретного пользователя)
        if (pt.submissionForPerson != null) {
          ParsedSubmission sub = pt.submissionForPerson;

          TaskSubmissionId tsId = new TaskSubmissionId(task.getId(), person.getId());
          // Попробуем найти в БД, если уже есть
          TaskSubmission existingSubmission = taskSubmissionRepository.findById(tsId).orElse(null);
          if (existingSubmission == null) {
            existingSubmission = new TaskSubmission();
            existingSubmission.setId(tsId);
            existingSubmission.setTask(task);
            existingSubmission.setPerson(person);
          }
          existingSubmission.setSubmissionStatus(sub.submissionStatus);
          existingSubmission.setGradingStatus(sub.gradingStatus);
          existingSubmission.setSubmissionDate(sub.submissionDate);
          existingSubmission.setMark(sub.mark);
          existingSubmission.setMaxMark(sub.maxMark);

          submissionsToSave.add(existingSubmission);
        }
      }
    }

    // Сохраняем все Tasks (с вложениями)
    taskRepository.saveAll(tasksToSave);
    taskRepository.flush(); // чтобы у новых Task были ID для связки TaskSubmission

    // Сохраняем все TaskSubmission
    taskSubmissionRepository.saveAll(submissionsToSave);

    // --- 2.3. Мержим группу (StudentGroup), если нашли
    if (extractedGroupName != null && !extractedGroupName.isBlank()) {
      StudentGroup group = studentGroupRepository.findByName(extractedGroupName).orElse(null);
      if (group == null) {
        group = new StudentGroup();
        group.setName(extractedGroupName);
        group = studentGroupRepository.save(group);
      }
      person.setGroup(group);
    }

    // --- 2.4. Сохраняем изменения Person (ФИО, moodleSession, group)
    personRepository.save(person);

    System.out.println("Все данные успешно собраны и сохранены одним набором запросов!");
  }

  /**
   * Извлекает текст из TD, который идёт после TH с нужным label.
   */
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

  /**
   * Парсим оценку вида "5,00 / 5,00" (или "4.5 / 5").
   */
  private Float[] parseGrade(String text) {
    if (text == null) return new Float[]{null, null};
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

  /**
   * Извлекает дату вида "1 сентября 2022, 00:00" (русские месяцы).
   */
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

  /**
   * Преобразует строку вида "(2025 Осенний семестр)" в LocalDate (для удобства).
   */
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

  // ======================
  // Вспомогательные классы для хранения данных в памяти
  // ======================

  /**
   * Промежуточная модель "Предмет", спарсенный с /my/.
   */
  private static class ParsedSubject {
    String assignmentsUrl;
    String name;
    LocalDate semesterDate;
    List<ParsedTask> tasks = new ArrayList<>();
  }

  /**
   * Промежуточная модель "Задание".
   */
  private static class ParsedTask {
    String assignmentsUrl;
    String name;
    Timestamp deadline;
    String description;
    List<ParsedAttachment> attachments = new ArrayList<>();
    ParsedSubmission submissionForPerson; // Submission для текущего пользователя
  }

  /**
   * Промежуточная модель "Вложение" (файлы в задании).
   */
  private static class ParsedAttachment {
    String fileUrl;
    String fileName;
    String fileExtension;
  }

  /**
   * Промежуточная модель "Отправленная работа" (TaskSubmission).
   */
  private static class ParsedSubmission {
    Long personId;
    String submissionStatus;
    String gradingStatus;
    Float mark;
    Float maxMark;
    Timestamp submissionDate;
  }
}
