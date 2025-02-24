package back.services;

import back.dto.LoginRequest;
import back.entities.Enrollment;
import back.entities.EnrollmentId;
import back.entities.Person;
import back.entities.Subject;
import back.repositories.PersonRepository;
import back.repositories.SubjectRepository;
import back.repositories.EnrollmentRepository;
import back.util.SeleniumUtil;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserParsingService {

  private final PersonRepository personRepository;
  private final SubjectRepository subjectRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final PageParsingService pageParsingService;

  @Async
  @Transactional
  public void parseAndUpdateUser(LoginRequest loginRequest, long id) {
    // Находим ученика (Person) по id
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
      // Загружаем HTML-страницу (например, личный кабинет)
      String url = "https://lms.sfedu.ru/my/";
      Document document = pageParsingService.parsePage(url, moodleSession);

      // Сохраняем HTML для отладки
      String filePath = "parsed_page.html";
      pageParsingService.saveDocumentToFile(document, filePath);
      System.out.println("Парсенная страница сохранена в " + new File(filePath).getAbsolutePath());

      // Выбираем только те ссылки, у которых есть дочерний элемент <span class="coc-metainfo">
      Elements courseLinks = document.select("a[title][href]:has(span.coc-metainfo)");
      for (Element link : courseLinks) {
        String href = link.attr("href").trim();
        String title = link.attr("title").trim();

        // Извлекаем текст семестра
        Element span = link.selectFirst("span.coc-metainfo");
        String semesterText = (span != null) ? span.text().trim() : null;
        LocalDate semesterDate = convertSemesterTextToDate(semesterText);

        // Если дату распознать не удалось – пропускаем этот элемент
        if (semesterDate == null) {
          continue;
        }

        // Если предмет с такой ссылкой уже существует, обновляем его; иначе создаем новый
        Subject subject = subjectRepository.findByAssignmentsUrl(href);
        if (subject == null) {
          subject = new Subject();
          subject.setAssignmentsUrl(href);
          subject.setName(title);
          subject.setSemesterDate(java.sql.Date.valueOf(semesterDate));
          subjectRepository.save(subject);
        } else {
          // Обновляем данные предмета
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
          // Устанавливаем начальные оценки (например, 0)
          enrollment.setMark(0f);
          enrollment.setMaxMark(0f);
          enrollmentRepository.save(enrollment);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
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
    // Убираем круглые скобки
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
