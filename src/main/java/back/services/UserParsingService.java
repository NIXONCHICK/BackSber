package back.services;

import back.dto.RegisterRequest;
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
  private final StudentTaskAssignmentRepository studentTaskAssignmentRepository;
  private final TaskGradingRepository taskGradingRepository;
  private final PageParsingService pageParsingService;


  @Async
  public void parseAndUpdateUser(RegisterRequest registerRequest, long personId) {
    Optional<Person> personOptional = personRepository.findById(personId);
    if (personOptional.isEmpty()) {
      return;
    }
    Person person = personOptional.get();

    String moodleSession = SeleniumUtil.loginAndGetMoodleSession(
        registerRequest.getEmail(), registerRequest.getPassword()
    );
    person.setMoodleSession(moodleSession);

    String myUrl = "https://lms.sfedu.ru/my/";
    Document myDoc;
    try {
      myDoc = pageParsingService.parsePage(myUrl, moodleSession);
      System.out.println("Страница 'My' успешно загружена.");
    } catch (Exception e) {
      e.printStackTrace();
      return;
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

    for (ParsedSubject parsedSubject : parsedSubjectsMap.values()) {
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


    for (ParsedSubject parsedSubject : parsedSubjectsMap.values()) {
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

        } catch (Exception e) {
          e.printStackTrace();
        }
      }
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
    }


    Set<String> subjectUrls = parsedSubjectsMap.keySet();
    List<Subject> existingSubjects = subjectRepository.findAllByAssignmentsUrlIn(subjectUrls);
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
    }

    subjectRepository.saveAll(subjectsToSave);
    subjectRepository.flush(); // чтобы у новых Subject появились ID

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
        task.setSource(TaskSource.PARSED);

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
    
    for (ParsedSubject ps : parsedSubjectsMap.values()) {
      for (ParsedTask pt : ps.tasks) {
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
    
    for (ParsedSubject ps : parsedSubjectsMap.values()) {
      for (ParsedTask pt : ps.tasks) {
        Task task = urlToTaskMap.get(pt.assignmentsUrl);
        if (task == null || task.getId() == null) {
          continue;
        }
        
        if (pt.submissionForPerson != null) {
          ParsedSubmission sub = pt.submissionForPerson;
          
          StudentTaskAssignmentId assignmentId = new StudentTaskAssignmentId(task.getId(), person.getId());
          StudentTaskAssignment assignment = studentTaskAssignmentRepository.findById(assignmentId).orElse(null);
          
          if (assignment != null) {
            // Search for existing grading or create new one
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
    
    // Save all gradings
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


  private static class ParsedSubject {
    String assignmentsUrl;
    String name;
    LocalDate semesterDate;
    List<ParsedTask> tasks = new ArrayList<>();
  }


  private static class ParsedTask {
    String assignmentsUrl;
    String name;
    Timestamp deadline;
    String description;
    List<ParsedAttachment> attachments = new ArrayList<>();
    ParsedSubmission submissionForPerson;
  }


  private static class ParsedAttachment {
    String fileUrl;
    String fileName;
    String fileExtension;
  }


  private static class ParsedSubmission {
    Long personId;
    String submissionStatus;
    String gradingStatus;
    Float mark;
    Float maxMark;
    Timestamp submissionDate;
  }
}