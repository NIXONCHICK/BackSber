package back.services;

import back.dto.courses.SemesterDto;
import back.dto.courses.SubjectDto;
import back.dto.courses.TaskDto;
import back.entities.*;
import back.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseQueryService {

    private final PersonRepository personRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SubjectRepository subjectRepository;
    private final TaskRepository taskRepository;
    private final StudentTaskAssignmentRepository studentTaskAssignmentRepository;
    private final TaskGradingRepository taskGradingRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public List<SemesterDto> getUserSemesters(Person person) {
        List<Enrollment> enrollments = enrollmentRepository.findAllByPersonId(person.getId());
        if (enrollments.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Date, List<Subject>> subjectsBySemesterDate = enrollments.stream()
                .map(Enrollment::getSubject)
                .collect(Collectors.groupingBy(Subject::getSemesterDate));

        Map<Date, List<Subject>> sortedSubjectsBySemesterDate = new LinkedHashMap<>();
        subjectsBySemesterDate.entrySet().stream()
            .sorted(Map.Entry.<Date, List<Subject>>comparingByKey().reversed())
            .forEachOrdered(e -> sortedSubjectsBySemesterDate.put(e.getKey(), e.getValue()));

        return sortedSubjectsBySemesterDate.entrySet().stream()
                .map(entry -> {
                    Date sqlDate = entry.getKey();
                    List<Subject> subjectsInGroup = entry.getValue();
                    LocalDate localDate = sqlDate.toLocalDate();
                    String semesterId = localDate.format(DATE_FORMATTER);
                    String semesterName = formatSemesterName(localDate);

                    String lastRefreshTimestampStr = null;
                    if (!subjectsInGroup.isEmpty()) {
                        Subject firstSubject = subjectsInGroup.get(0);
                        if (firstSubject.getLastAiRefreshTimestamp() != null) {
                            lastRefreshTimestampStr = firstSubject.getLastAiRefreshTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        }
                    }
                    return new SemesterDto(semesterId, semesterName, lastRefreshTimestampStr);
                })
                .collect(Collectors.toList());
    }

    private String formatSemesterName(LocalDate semesterStartDate) {
        int year = semesterStartDate.getYear();
        int month = semesterStartDate.getMonthValue();
        if (month >= 9 || month == 1) {
            return String.format("%d-%d Осенний", year, year + 1);
        } else {
            return String.format("%d Весенний", year);
        }
    }

    public List<SubjectDto> getSubjectsForSemester(Person person, String semesterId) {
        LocalDate semesterStartDate;
        try {
            semesterStartDate = LocalDate.parse(semesterId, DATE_FORMATTER);
        } catch (Exception e) {
            // Handle invalid semesterId format
            return Collections.emptyList();
        }

        List<Enrollment> enrollments = enrollmentRepository.findAllByPersonId(person.getId());
        return enrollments.stream()
                .map(Enrollment::getSubject)
                .filter(subject -> subject.getSemesterDate() != null && subject.getSemesterDate().toLocalDate().equals(semesterStartDate))
                .sorted(Comparator.comparing(Subject::getName, String.CASE_INSENSITIVE_ORDER))
                .map(subject -> new SubjectDto(subject.getId(), subject.getName()))
                .collect(Collectors.toList());
    }

    public List<TaskDto> getTasksForSubject(Person person, Long subjectId) {
        List<Task> tasks = taskRepository.findAllBySubjectId(subjectId);
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }

        List<TaskDto> taskDtos = new ArrayList<>();
        for (Task task : tasks) {
            StudentTaskAssignment assignment = studentTaskAssignmentRepository
                    .findByTask_IdAndPerson_Id(task.getId(), person.getId())
                    .orElse(null);

            String status = "Не сдано";
            String grade = null;
            String description = task.getDescription();
            String deadline = task.getDeadline() != null ? 
                              task.getDeadline().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMATTER) 
                              : null;

            if (assignment != null) {
                TaskGrading grading = taskGradingRepository.findByAssignment(assignment);
                if (grading != null) {
                    if (grading.getMark() != null || (grading.getGradingStatus() != null && (grading.getGradingStatus().toLowerCase().contains("оценен") || grading.getGradingStatus().toLowerCase().contains("зачет")))) {
                        status = "Оценено";
                        if (grading.getMark() != null && grading.getMaxMark() != null) {
                             grade = grading.getMark() + "/" + grading.getMaxMark();
                        } else if (grading.getMark() != null) {
                             grade = String.valueOf(grading.getMark());
                        } else if (grading.getGradingStatus() != null && grading.getGradingStatus().toLowerCase().contains("зачет")){
                             grade = "Зачет";
                             status = "Зачет";
                        } else {
                             grade = "Оценено";                         }
                    } else if (grading.getSubmissionStatus() != null && !grading.getSubmissionStatus().isEmpty() && !grading.getSubmissionStatus().toLowerCase().contains("нет ответа")) {
                                                 status = "Сдано";
                    }
                                                        }
            }
            String originalTaskName = task.getName();
            String processedTaskName = originalTaskName;
            if (originalTaskName != null && originalTaskName.endsWith(" Задание")) {
                processedTaskName = originalTaskName.substring(0, originalTaskName.length() - " Задание".length());
            }
            taskDtos.add(new TaskDto(task.getId(), processedTaskName, deadline, status, grade, description, task.getEstimatedMinutes(), task.getTimeEstimateExplanation()));
        }
        taskDtos.sort(Comparator
            .comparing((TaskDto t) -> t.getDeadline() == null ? null : LocalDate.parse(t.getDeadline()), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TaskDto::getName, String.CASE_INSENSITIVE_ORDER));
        return taskDtos;
    }
} 