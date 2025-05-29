package back.repositories;

import back.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("TaskRepository Integration Tests")
class TaskRepositoryIntegrationTest {

    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private PersonRepository personRepository;
    
    @Autowired
    private SubjectRepository subjectRepository;
    
    @Autowired
    private StudentGroupRepository studentGroupRepository;
    
    @Autowired
    private StudentTaskAssignmentRepository studentTaskAssignmentRepository;
    
    @Autowired
    private TestEntityManager entityManager;

    private Person testStudent1;
    private Person testStudent2;
    private Subject testSubject;
    private StudentGroup testGroup;
    private Task task1, task2, task3;
    private java.util.Date today;
    private java.util.Date tomorrow;
    private java.util.Date yesterday;

    @BeforeEach
    void setUp() {
        Calendar cal = Calendar.getInstance();
        today = cal.getTime();
        
        cal.add(Calendar.DAY_OF_MONTH, 1);
        tomorrow = cal.getTime();
        
        cal.add(Calendar.DAY_OF_MONTH, -2);
        yesterday = cal.getTime();

        testGroup = new StudentGroup();
        testGroup.setName("ТИ-201");
        testGroup = studentGroupRepository.save(testGroup);

        testStudent1 = new Person();
        testStudent1.setName("Иван");
        testStudent1.setSurname("Иванов");
        testStudent1.setEmail("ivanov@sfedu.ru");
        testStudent1.setPassword("encrypted_password");
        testStudent1.setRole(Role.STUDENT);
        testStudent1.setGroup(testGroup);
        testStudent1.setAccountNonExpired(true);
        testStudent1.setAccountNonLocked(true);
        testStudent1.setCredentialsNonExpired(true);
        testStudent1.setEnabled(true);
        testStudent1 = personRepository.save(testStudent1);

        testStudent2 = new Person();
        testStudent2.setName("Петр");
        testStudent2.setSurname("Петров");
        testStudent2.setEmail("petrov@sfedu.ru");
        testStudent2.setPassword("encrypted_password");
        testStudent2.setRole(Role.STUDENT);
        testStudent2.setGroup(testGroup);
        testStudent2.setAccountNonExpired(true);
        testStudent2.setAccountNonLocked(true);
        testStudent2.setCredentialsNonExpired(true);
        testStudent2.setEnabled(true);
        testStudent2 = personRepository.save(testStudent2);

        testSubject = new Subject();
        testSubject.setName("Программирование");
        testSubject.setAssignmentsUrl("https://lms.sfedu.ru/course/view.php?id=123");
        testSubject.setSemesterDate(Date.valueOf(LocalDate.of(2024, 9, 1)));
        testSubject = subjectRepository.save(testSubject);

        task1 = new Task();
        task1.setName("Лабораторная работа 1");
        task1.setDescription("Основы Java");
        task1.setDeadline(tomorrow);
        task1.setSubject(testSubject);
        task1.setSource(TaskSource.PARSED);
        task1.setAssignmentsUrl("https://lms.sfedu.ru/assignment/view.php?id=1");
        task1 = taskRepository.save(task1);

        task2 = new Task();
        task2.setName("Лабораторная работа 2");
        task2.setDescription("ООП в Java");
        task2.setDeadline(yesterday);
        task2.setSubject(testSubject);
        task2.setSource(TaskSource.PARSED);
        task2.setAssignmentsUrl("https://lms.sfedu.ru/assignment/view.php?id=2");
        task2 = taskRepository.save(task2);

        task3 = new Task();
        task3.setName("Контрольная работа");
        task3.setDescription("Итоговая проверка знаний");
        Calendar nextWeek = Calendar.getInstance();
        nextWeek.add(Calendar.WEEK_OF_YEAR, 1);
        task3.setDeadline(nextWeek.getTime());
        task3.setSubject(testSubject);
        task3.setSource(TaskSource.USER);
        task3.setAssignmentsUrl("https://lms.sfedu.ru/assignment/view.php?id=3");
        task3 = taskRepository.save(task3);

        StudentTaskAssignmentId assignmentId1 = new StudentTaskAssignmentId(task1.getId(), testStudent1.getId());
        StudentTaskAssignment assignment1 = new StudentTaskAssignment();
        assignment1.setId(assignmentId1);
        assignment1.setTask(task1);
        assignment1.setPerson(testStudent1);
        studentTaskAssignmentRepository.save(assignment1);

        StudentTaskAssignmentId assignmentId2 = new StudentTaskAssignmentId(task2.getId(), testStudent1.getId());
        StudentTaskAssignment assignment2 = new StudentTaskAssignment();
        assignment2.setId(assignmentId2);
        assignment2.setTask(task2);
        assignment2.setPerson(testStudent1);
        studentTaskAssignmentRepository.save(assignment2);

        StudentTaskAssignmentId assignmentId3 = new StudentTaskAssignmentId(task3.getId(), testStudent1.getId());
        StudentTaskAssignment assignment3 = new StudentTaskAssignment();
        assignment3.setId(assignmentId3);
        assignment3.setTask(task3);
        assignment3.setPerson(testStudent1);
        studentTaskAssignmentRepository.save(assignment3);

        StudentTaskAssignmentId assignmentId4 = new StudentTaskAssignmentId(task1.getId(), testStudent2.getId());
        StudentTaskAssignment assignment4 = new StudentTaskAssignment();
        assignment4.setId(assignmentId4);
        assignment4.setTask(task1);
        assignment4.setPerson(testStudent2);
        studentTaskAssignmentRepository.save(assignment4);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Поиск заданий с дедлайнами для пользователя")
    void testFindTasksWithDeadlinesForUser() {
        List<Task> tasks = taskRepository.findTasksWithDeadlinesForUser(today, testStudent1.getId());

        assertThat(tasks).hasSize(2);
        assertThat(tasks)
                .extracting(Task::getName)
                .containsExactly("Лабораторная работа 1", "Контрольная работа");
        
        tasks.forEach(task -> {
            assertThat(task.getSubject()).isNotNull();
            assertThat(task.getSubject().getName()).isEqualTo("Программирование");
        });
    }

    @Test
    @DisplayName("Поиск задания по ID для конкретного пользователя")
    void testFindTaskByIdForUser() {
        Optional<Task> foundTask = taskRepository.findTaskByIdForUser(task1.getId(), testStudent1.getId());
        Optional<Task> notFoundTask = taskRepository.findTaskByIdForUser(task1.getId(), 999L);

        assertThat(foundTask).isPresent();
        assertThat(foundTask.get().getName()).isEqualTo("Лабораторная работа 1");
        assertThat(foundTask.get().getSubject()).isNotNull();
        
        assertThat(notFoundTask).isEmpty();
    }

    @Test
    @DisplayName("Поиск дублирующего задания пользователя")
    void testFindDuplicateUserTask() {
        Optional<Task> duplicate = taskRepository.findDuplicateUserTask(
                "Лабораторная работа 1", 
                tomorrow, 
                "Основы Java", 
                testStudent1.getId(),
                TaskSource.PARSED);
        
        Optional<Task> noDuplicate = taskRepository.findDuplicateUserTask(
                "Несуществующее задание", 
                tomorrow, 
                "Описание", 
                testStudent1.getId(),
                TaskSource.PARSED);

        assertThat(duplicate).isPresent();
        assertThat(duplicate.get().getId()).isEqualTo(task1.getId());
        
        assertThat(noDuplicate).isEmpty();
    }

    @Test
    @DisplayName("Поиск дублирующего задания старосты")
    void testFindDuplicateElderTask() {
        Optional<Task> duplicate = taskRepository.findDuplicateElderTask(
                "Лабораторная работа 1", 
                tomorrow, 
                "Основы Java", 
                testGroup.getId(),
                TaskSource.PARSED);

        assertThat(duplicate).isPresent();
        assertThat(duplicate.get().getId()).isEqualTo(task1.getId());
    }

    @Test
    @DisplayName("Поиск заданий по источнику, пользователю и семестру")
    void testFindTasksBySourceAndPersonIdAndSemesterDate() {
        List<Task> parsedTasks = taskRepository.findTasksBySourceAndPersonIdAndSemesterDate(
                TaskSource.PARSED, 
                testStudent1.getId(), 
                testSubject.getSemesterDate());
        
        List<Task> userTasks = taskRepository.findTasksBySourceAndPersonIdAndSemesterDate(
                TaskSource.USER, 
                testStudent1.getId(), 
                testSubject.getSemesterDate());

        assertThat(parsedTasks).hasSize(2);
        assertThat(parsedTasks)
                .extracting(Task::getSource)
                .allMatch(source -> source == TaskSource.PARSED);
        
        assertThat(userTasks).hasSize(1);
        assertThat(userTasks.get(0).getSource()).isEqualTo(TaskSource.USER);
    }

    @Test
    @DisplayName("Поиск всех заданий пользователя")
    void testFindTasksByPersonId() {
        List<Task> userTasks = taskRepository.findTasksByPersonId(testStudent1.getId());

        assertThat(userTasks).hasSize(3);
        assertThat(userTasks)
                .extracting(Task::getName)
                .containsExactlyInAnyOrder(
                        "Лабораторная работа 1", 
                        "Лабораторная работа 2", 
                        "Контрольная работа");
        
        userTasks.forEach(task -> {
            assertThat(task.getSubject()).isNotNull();
            assertThat(task.getSubject().getName()).isEqualTo("Программирование");
        });
    }

    @Test
    @DisplayName("Поиск заданий по URL")
    void testFindAllByAssignmentsUrlIn() {
        List<String> urls = List.of(
                "https://lms.sfedu.ru/assignment/view.php?id=1",
                "https://lms.sfedu.ru/assignment/view.php?id=3");
        List<Task> tasks = taskRepository.findAllByAssignmentsUrlIn(urls);

        assertThat(tasks).hasSize(2);
        assertThat(tasks)
                .extracting(Task::getName)
                .containsExactlyInAnyOrder("Лабораторная работа 1", "Контрольная работа");
    }

    @Test
    @DisplayName("Поиск заданий по предмету")
    void testFindAllBySubjectId() {
        List<Task> subjectTasks = taskRepository.findAllBySubjectId(testSubject.getId());

        assertThat(subjectTasks).hasSize(3);
        assertThat(subjectTasks)
                .extracting(Task::getName)
                .containsExactlyInAnyOrder(
                        "Лабораторная работа 1", 
                        "Лабораторная работа 2", 
                        "Контрольная работа");
    }
} 