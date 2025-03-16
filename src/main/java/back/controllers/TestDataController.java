package back.controllers;

import back.entities.*;
import back.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class TestDataController {

    private final TaskRepository taskRepository;
    private final SubjectRepository subjectRepository;
    private final PersonRepository personRepository;
    private final StudentTaskAssignmentRepository studentTaskAssignmentRepository;

    @PostMapping("/create-semester-task")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> createTestSemesterTask() {
        try {
            Subject subjectFall = new Subject();
            subjectFall.setName("Тестовый предмет (осенний семестр)");
            subjectFall.setAssignmentsUrl("https://test.com/fall");
            subjectFall.setSemesterDate(Date.valueOf(LocalDate.of(2022, 9, 1)));
            subjectRepository.save(subjectFall);

            Subject subjectSpring = new Subject();
            subjectSpring.setName("Тестовый предмет (весенний семестр)");
            subjectSpring.setAssignmentsUrl("https://test.com/spring");
            subjectSpring.setSemesterDate(Date.valueOf(LocalDate.of(2023, 2, 1)));
            subjectRepository.save(subjectSpring);

            Task taskFall = new Task();
            taskFall.setName("Тестовое задание (осенний семестр)");
            taskFall.setDescription("Это тестовое задание для осеннего семестра");
            taskFall.setDeadline(new java.util.Date());
            taskFall.setSource(TaskSource.PARSED);
            taskFall.setSubject(subjectFall);
            taskRepository.save(taskFall);

            Task taskSpring = new Task();
            taskSpring.setName("Тестовое задание (весенний семестр)");
            taskSpring.setDescription("Это тестовое задание для весеннего семестра");
            taskSpring.setDeadline(new java.util.Date());
            taskSpring.setSource(TaskSource.PARSED);
            taskSpring.setSubject(subjectSpring);
            taskRepository.save(taskSpring);

            Person currentUser = personRepository.findById(1L).orElseThrow();

            StudentTaskAssignment assignmentFall = new StudentTaskAssignment();
            assignmentFall.setId(new StudentTaskAssignmentId(taskFall.getId(), currentUser.getId()));
            assignmentFall.setTask(taskFall);
            assignmentFall.setPerson(currentUser);
            studentTaskAssignmentRepository.save(assignmentFall);

            StudentTaskAssignment assignmentSpring = new StudentTaskAssignment();
            assignmentSpring.setId(new StudentTaskAssignmentId(taskSpring.getId(), currentUser.getId()));
            assignmentSpring.setTask(taskSpring);
            assignmentSpring.setPerson(currentUser);
            studentTaskAssignmentRepository.save(assignmentSpring);

            log.info("Созданы тестовые задания для обоих семестров для пользователя {}", currentUser.getId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Тестовые задания созданы",
                    "taskFallId", taskFall.getId(),
                    "taskSpringId", taskSpring.getId()
            ));

        } catch (Exception e) {
            log.error("Ошибка при создании тестовых данных: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Ошибка: " + e.getMessage()
            ));
        }
    }
} 