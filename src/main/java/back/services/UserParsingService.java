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
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserParsingService {

  private final PersonRepository personRepository;
  private final SubjectRepository subjectRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final StudentGroupRepository studentGroupRepository;
  private final TaskRepository taskRepository;
  private final TaskSubmissionRepository taskSubmissionRepository;
  private final PageParsingService pageParsingService;

  @Async
  @Transactional
  public void parseAndUpdateUser(LoginRequest loginRequest, long id) {
    Optional<Person> personOptional = personRepository.findById(id);
    if (personOptional.isEmpty()) {
      return;
    }
    Person person = personOptional.get();

    // 1) Получаем MoodleSession через Selenium
    String moodleSession = SeleniumUtil.loginAndGetMoodleSession(
        loginRequest.getEmail(), loginRequest.getPassword());
    person.setMoodleSession(moodleSession);
    personRepository.save(person);

    try {
      // 2) Загружаем страницу "My"
      String url = "https://lms.sfedu.ru/my/";
      Document document = pageParsingService.parsePage(url, moodleSession);
      System.out.println("Страница 'My' успешно загружена.");

      // 3) Парсим предметы (курсы)
      Elements courseLinks = document.select("a[title][href]:has(span.coc-metainfo)");
      for (Element link : courseLinks) {
        String href = link.attr("href").trim();
        String title = link.attr("title").trim();

        // Достаем семестр (например, "(2025 Осенний семестр)")
        Element span = link.selectFirst("span.coc-metainfo");
        String semesterText = (span != null) ? span.text().trim() : null;
        LocalDate semesterDate = convertSemesterTextToDate(semesterText);
        if (semesterDate == null) {
          continue;
        }

        Subject subject = subjectRepository.findByAssignmentsUrl(href);
        if (subject == null) {
          subject = new Subject();
          subject.setAssignmentsUrl(href);
          subject.setName(title);
          subject.setSemesterDate(java.sql.Date.valueOf(semesterDate));
          subjectRepository.save(subject);
        } else {
          subject.setName(title);
          subject.setSemesterDate(java.sql.Date.valueOf(semesterDate));
          subjectRepository.save(subject);
        }

        // Создаем связь между учеником и предметом через Enrollment, если её ещё нет
        Enrollment enrollment = enrollmentRepository.findByPersonIdAndSubjectId(person.getId(), subject.getId());
        if (enrollment == null) {
          enrollment = new Enrollment();
          EnrollmentId enrollmentId = new EnrollmentId(person.getId(), subject.getId());
          enrollment.setId(enrollmentId);
          enrollment.setPerson(person);
          enrollment.setSubject(subject);
          enrollmentRepository.save(enrollment);
        }
      }

      // 4) Для каждого предмета парсим задания и их детали
      for (Subject subject : subjectRepository.findByPersonId(person.getId())) {
        parseAssignmentsForSubject(subject, person, moodleSession);
      }

      // 5) Парсинг страницы профиля (ФИО, группа)
      parseProfile(person, moodleSession);
      personRepository.save(person);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Парсит список заданий для данного предмета и переходит на страницу каждого задания для детального парсинга.
   */
  private void parseAssignmentsForSubject(Subject subject, Person person, String moodleSession) throws Exception {
    String assignmentsUrl = subject.getAssignmentsUrl();
    if (assignmentsUrl == null || assignmentsUrl.isEmpty()) {
      return;
    }

    Document subjectDoc = pageParsingService.parsePage(assignmentsUrl, moodleSession);
    System.out.println("Страница предмета \"" + subject.getName() + "\" успешно загружена.");

    // Выбираем ссылки на задания с классами "aalink" и "stretched-link"
    Elements assignmentLinks = subjectDoc.select("a.aalink.stretched-link");
    for (Element assignmentLink : assignmentLinks) {
      String taskHref = assignmentLink.attr("href").trim();
      // Обрабатываем только ссылки, начинающиеся с "https://lms.sfedu.ru/mod/assign/view.php"
      if (!taskHref.startsWith("https://lms.sfedu.ru/mod/assign/view.php")) {
        continue;
      }
      String taskName = "";
      Element nameElement = assignmentLink.selectFirst("span.instancename");
      if (nameElement != null) {
        taskName = nameElement.text().trim();
      }

      Task task = taskRepository.findByAssignmentsUrl(taskHref);
      if (task == null) {
        task = new Task();
        task.setAssignmentsUrl(taskHref);
        task.setName(taskName);
        task.setSubject(subject);
        taskRepository.save(task);
      } else {
        task.setName(taskName);
        taskRepository.save(task);
      }

      // Парсим детали задания (дедлайн, описание, прикреплённые файлы, состояния и оценку)
      parseAssignmentDetails(task, person, moodleSession);
    }
  }

  /**
   * Переходим на страницу задания (assign/view.php?id=...) и парсим:
   * - дедлайн (общий для задания),
   * - описание задания,
   * - прикрепленные файлы (ссылка, название, расширение),
   * - состояние ответа на задание,
   * - состояние оценивания,
   * - оценку (например, "5,00 / 5,00").
   * Данные о дедлайне и описании сохраняются в Task,
   * а остальные – в TaskSubmission (пользовательские данные).
   */
  private void parseAssignmentDetails(Task task, Person person, String moodleSession) throws Exception {
    String url = task.getAssignmentsUrl();
    if (url == null || url.isEmpty()) {
      return;
    }
    Document doc = pageParsingService.parsePage(url, moodleSession);
    System.out.println("Страница задания \"" + task.getName() + "\" для пользователя " + person.getEmail() + " успешно загружена.");

    // 1) Парсим дедлайн задания (общий для задания)
    Element deadlineDiv = doc.selectFirst("div:has(strong:containsOwn(Срок сдачи))");
    if (deadlineDiv != null) {
      String text = deadlineDiv.text().replace("Срок сдачи:", "").trim();
      LocalDateTime dt = parseDeadlineText(text);
      if (dt != null) {
        task.setDeadline(Timestamp.valueOf(dt));
        taskRepository.save(task);
      }
    }

    // 2) Парсим описание задания и прикрепленные файлы
    Element descriptionBlock = doc.selectFirst("div.box.py-3.generalbox.boxaligncenter");
    if (descriptionBlock != null) {
      // Извлекаем описание из блока с классом "no-overflow"
      Element noOverflowDiv = descriptionBlock.selectFirst("div.no-overflow");
      if (noOverflowDiv != null) {
        String descriptionText = noOverflowDiv.text().trim();
        task.setDescription(descriptionText);
        taskRepository.save(task);
      }
      // Извлекаем прикрепленные файлы из блока, id которого начинается с "assign_files_tree"
      Element filesTree = descriptionBlock.selectFirst("div[id^=assign_files_tree]");
      if (filesTree != null) {
        Elements attachmentLinks = filesTree.select("a[target=_blank]");
        // Инициализируем список вложений, чтобы избежать дублирования
        if (task.getAttachments() == null) {
          task.setAttachments(new ArrayList<>());
        } else {
          task.getAttachments().clear();
        }
        for (Element a : attachmentLinks) {
          String fileUrl = a.attr("href").trim();
          String fileName = a.text().trim();
          String fileExtension = "";
          int lastDotIndex = fileName.lastIndexOf('.');
          if (lastDotIndex != -1 && lastDotIndex < fileName.length() - 1) {
            fileExtension = fileName.substring(lastDotIndex + 1);
          }
          TaskAttachment attachment = new TaskAttachment();
          attachment.setTask(task);
          attachment.setFileUrl(fileUrl);
          attachment.setFileName(fileName);
          attachment.setFileExtension(fileExtension);
          task.getAttachments().add(attachment);
        }
        taskRepository.save(task);
      }
    }

    // 3) Парсим пользовательские данные: состояние ответа, оценивания и оценку
    String submissionStatus = textFromAdjacentTd(doc, "Состояние ответа на задание");
    String gradingStatus = textFromAdjacentTd(doc, "Состояние оценивания");
    String gradeText = textFromAdjacentTd(doc, "Оценка");

    Float[] marks = parseGrade(gradeText);

    TaskSubmissionId tsId = new TaskSubmissionId(task.getId(), person.getId());
    TaskSubmission submission = taskSubmissionRepository.findById(tsId).orElse(null);
    if (submission == null) {
      submission = new TaskSubmission();
      submission.setId(tsId);
      submission.setTask(task);
      submission.setPerson(person);
    }
    submission.setSubmissionStatus(submissionStatus);
    submission.setGradingStatus(gradingStatus);
    if (marks[0] != null) {
      submission.setMark(marks[0]);
    }
    if (marks[1] != null) {
      submission.setMaxMark(marks[1]);
    }
    taskSubmissionRepository.save(submission);
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
   * Извлекает первую подходящую дату из текста дедлайна.
   * Ожидаемый шаблон: "1 сентября 2022, 00:00"
   */
  private LocalDateTime parseDeadlineText(String text) {
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

  private void parseProfile(Person person, String moodleSession) throws Exception {
    String profileUrl = "https://lms.sfedu.ru/user/profile.php";
    Document profileDocument = pageParsingService.parsePage(profileUrl, moodleSession);
    System.out.println("Страница профиля успешно загружена.");

    // Извлекаем ФИО из <h1 class="h2">
    Element h1 = profileDocument.selectFirst("h1.h2");
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

    // Извлекаем группу: ищем <dl> с <dt> "Группа" и берем его <dd>
    Element groupElement = profileDocument.selectFirst("dl:has(dt:containsOwn(Группа)) dd");
    if (groupElement != null) {
      String groupName = groupElement.text().trim();
      if (!groupName.isEmpty()) {
        StudentGroup group = studentGroupRepository.findByName(groupName).orElse(null);
        if (group == null) {
          group = new StudentGroup();
          group.setName(groupName);
          group = studentGroupRepository.save(group);
        }
        person.setGroup(group);
      }
    }
  }

  /**
   * Преобразует строку вида "(2025 Осенний семестр)" в LocalDate.
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
}
