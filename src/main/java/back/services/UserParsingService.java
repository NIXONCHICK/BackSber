package back.services;

import back.dto.LoginRequest;
import back.entities.Enrollment;
import back.entities.EnrollmentId;
import back.entities.Person;
import back.entities.StudentGroup;
import back.entities.Subject;
import back.entities.Task;
import back.repositories.EnrollmentRepository;
import back.repositories.PersonRepository;
import back.repositories.StudentGroupRepository;
import back.repositories.SubjectRepository;
import back.repositories.TaskRepository;
import back.util.SeleniumUtil;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserParsingService {

  private final PersonRepository personRepository;
  private final SubjectRepository subjectRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final StudentGroupRepository studentGroupRepository;
  private final TaskRepository taskRepository;
  private final PageParsingService pageParsingService;

  @Async
  @Transactional
  public void parseAndUpdateUser(LoginRequest loginRequest, long id) {
    Optional<Person> personOptional = personRepository.findById(id);
    if (personOptional.isEmpty()) {
      return;
    }
    Person person = personOptional.get();

    // Получаем MoodleSession через Selenium
    String moodleSession = SeleniumUtil.loginAndGetMoodleSession(
        loginRequest.getEmail(), loginRequest.getPassword());
    person.setMoodleSession(moodleSession);
    personRepository.save(person);

    try {
      // Загружаем страницу "My"
      String url = "https://lms.sfedu.ru/my/";
      Document document = pageParsingService.parsePage(url, moodleSession);

      // Сохраняем HTML для отладки
      String filePath = "parsed_page.html";
      pageParsingService.saveDocumentToFile(document, filePath);
      System.out.println("Парсенная страница сохранена в " + Paths.get(filePath).toAbsolutePath());

      // Парсим предметы (курсы)
      Elements courseLinks = document.select("a[title][href]:has(span.coc-metainfo)");
      for (Element link : courseLinks) {
        String href = link.attr("href").trim();
        String title = link.attr("title").trim();

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

        // Создаем связь между учеником и предметом через Enrollment, если она ещё не существует
        Enrollment enrollment = enrollmentRepository.findByPersonIdAndSubjectId(person.getId(), subject.getId());
        if (enrollment == null) {
          enrollment = new Enrollment();
          EnrollmentId enrollmentId = new EnrollmentId();
          enrollmentId.setPersonId(person.getId());
          enrollmentId.setSubjectId(subject.getId());
          enrollment.setId(enrollmentId);
          enrollment.setPerson(person);
          enrollment.setSubject(subject);
          enrollmentRepository.save(enrollment);
        }
      }

      // Парсинг заданий для каждого предмета
      // Обрабатываем только ссылки, начинающиеся с нужного паттерна
      for (Subject subject : subjectRepository.findByPersonId(person.getId())) {
        parseAssignments(subject, moodleSession);
      }

      // Парсинг страницы профиля для получения ФИО и группы
      parseProfile(person, moodleSession);
      personRepository.save(person);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Парсит задания (без дедлайнов) для данного предмета.
   * Обрабатываются только ссылки, начинающиеся с "https://lms.sfedu.ru/mod/resource/view.php".
   */
  private void parseAssignments(Subject subject, String moodleSession) throws Exception {
    String assignmentsUrl = subject.getAssignmentsUrl();
    if (assignmentsUrl == null || assignmentsUrl.isEmpty()) {
      return;
    }

    Document subjectDoc = pageParsingService.parsePage(assignmentsUrl, moodleSession);
    String subjectFilePath = "parsed_subject_" + subject.getId() + ".html";
    pageParsingService.saveDocumentToFile(subjectDoc, subjectFilePath);
    System.out.println("Страница предмета " + subject.getName() + " сохранена в " +
        Paths.get(subjectFilePath).toAbsolutePath());

    // Выбираем ссылки с классами "aalink" и "stretched-link"
    Elements assignmentLinks = subjectDoc.select("a.aalink.stretched-link");
    for (Element assignmentLink : assignmentLinks) {
      String taskHref = assignmentLink.attr("href").trim();
      // Фильтруем: обрабатываем только ссылки, начинающиеся с указанного паттерна
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
    }
  }

  private void parseProfile(Person person, String moodleSession) throws Exception {
    String profileUrl = "https://lms.sfedu.ru/user/profile.php";
    Document profileDocument = pageParsingService.parsePage(profileUrl, moodleSession);
    String profileFilePath = "parsed_profile_page.html";
    pageParsingService.saveDocumentToFile(profileDocument, profileFilePath);
    System.out.println("Страница профиля сохранена в " + Paths.get(profileFilePath).toAbsolutePath());

    // Извлекаем ФИО из заголовка <h1 class="h2">
    Element h1 = profileDocument.selectFirst("h1.h2");
    if (h1 != null) {
      String fullName = h1.text().trim();
      String[] nameParts = fullName.split(" ");
      if (nameParts.length >= 2) {
        person.setSurname(nameParts[0]);
        person.setName(nameParts[1]);
        if (nameParts.length >= 3) {
          person.setPatronymic(nameParts[2]);
        }
      }
    }

    // Извлекаем группу: ищем элемент <dl> с <dt> "Группа" и берем его <dd>
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
   * Преобразует строку вида "(2021 Осенний семестр)" в LocalDate.
   * Для осеннего семестра возвращает 2021-09-01, для весеннего – 2021-02-01.
   */
  private LocalDate convertSemesterTextToDate(String semesterText) {
    if (semesterText == null || semesterText.isEmpty()) {
      return null;
    }
    semesterText = semesterText.replace("(", "").replace(")", "").trim();
    String[] parts = semesterText.split(" ");
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
