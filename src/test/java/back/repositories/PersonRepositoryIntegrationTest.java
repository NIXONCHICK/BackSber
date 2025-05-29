package back.repositories;

import back.entities.Person;
import back.entities.Role;
import back.entities.StudentGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("PersonRepository Integration Tests")
class PersonRepositoryIntegrationTest {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private StudentGroupRepository studentGroupRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Person testStudent1;
    private Person testStudent2;
    private Person testTeacher;
    private StudentGroup testGroup;

    @BeforeEach
    void setUp() {
        testGroup = new StudentGroup();
        testGroup.setName("ТИ-201");
        testGroup = studentGroupRepository.save(testGroup);

        testStudent1 = new Person();
        testStudent1.setName("Иван");
        testStudent1.setSurname("Иванов");
        testStudent1.setPatronymic("Иванович");
        testStudent1.setEmail("ivanov@sfedu.ru");
        testStudent1.setPassword("encrypted_password_123");
        testStudent1.setRole(Role.STUDENT);
        testStudent1.setGroup(testGroup);
        testStudent1.setMoodleSession("session_123");
        testStudent1.setAccountNonExpired(true);
        testStudent1.setAccountNonLocked(true);
        testStudent1.setCredentialsNonExpired(true);
        testStudent1.setEnabled(true);

        testStudent2 = new Person();
        testStudent2.setName("Петр");
        testStudent2.setSurname("Петров");
        testStudent2.setPatronymic("Петрович");
        testStudent2.setEmail("petrov@sfedu.ru");
        testStudent2.setPassword("encrypted_password_456");
        testStudent2.setRole(Role.STUDENT);
        testStudent2.setGroup(testGroup);
        testStudent2.setMoodleSession("session_456");
        testStudent2.setAccountNonExpired(true);
        testStudent2.setAccountNonLocked(true);
        testStudent2.setCredentialsNonExpired(true);
        testStudent2.setEnabled(true);

        testTeacher = new Person();
        testTeacher.setName("Анна");
        testTeacher.setSurname("Анненко");
        testTeacher.setPatronymic("Алексеевна");
        testTeacher.setEmail("teacher@sfedu.ru");
        testTeacher.setPassword("encrypted_password_789");
        testTeacher.setRole(Role.ELDER);
        testTeacher.setGroup(null);
        testTeacher.setMoodleSession("session_789");
        testTeacher.setAccountNonExpired(true);
        testTeacher.setAccountNonLocked(true);
        testTeacher.setCredentialsNonExpired(true);
        testTeacher.setEnabled(true);
    }

    @Test
    @DisplayName("Сохранение и получение пользователя по ID")
    void testSaveAndFindById() {
        Person savedPerson = personRepository.save(testStudent1);
        entityManager.flush();
        entityManager.clear();

        Person foundPerson = personRepository.findById(savedPerson.getId()).orElse(null);

        assertThat(foundPerson).isNotNull();
        assertThat(foundPerson.getId()).isEqualTo(savedPerson.getId());
        assertThat(foundPerson.getEmail()).isEqualTo("ivanov@sfedu.ru");
        assertThat(foundPerson.getName()).isEqualTo("Иван");
        assertThat(foundPerson.getSurname()).isEqualTo("Иванов");
        assertThat(foundPerson.getRole()).isEqualTo(Role.STUDENT);
        assertThat(foundPerson.getGroup()).isNotNull();
        assertThat(foundPerson.getGroup().getName()).isEqualTo("ТИ-201");
    }

    @Test
    @DisplayName("Поиск пользователя по email")
    void testFindByEmail() {
        personRepository.save(testStudent1);
        entityManager.flush();
        entityManager.clear();

        Person foundPerson = personRepository.findByEmail("ivanov@sfedu.ru");

        assertThat(foundPerson).isNotNull();
        assertThat(foundPerson.getEmail()).isEqualTo("ivanov@sfedu.ru");
        assertThat(foundPerson.getName()).isEqualTo("Иван");
        assertThat(foundPerson.getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    @DisplayName("Поиск пользователя по несуществующему email")
    void testFindByEmail_NotExists() {
        personRepository.save(testStudent1);
        entityManager.flush();
        entityManager.clear();

        Person foundPerson = personRepository.findByEmail("nonexistent@sfedu.ru");

        assertThat(foundPerson).isNull();
    }

    @Test
    @DisplayName("Поиск всех пользователей по группе")
    void testFindAllByGroup() {
        personRepository.save(testStudent1);
        personRepository.save(testStudent2);
        personRepository.save(testTeacher);
        entityManager.flush();
        entityManager.clear();

        List<Person> studentsInGroup = personRepository.findAllByGroup(testGroup);

        assertThat(studentsInGroup).hasSize(2);
        assertThat(studentsInGroup)
                .extracting(Person::getEmail)
                .containsExactlyInAnyOrder("ivanov@sfedu.ru", "petrov@sfedu.ru");
        assertThat(studentsInGroup)
                .allMatch(person -> person.getGroup().getName().equals("ТИ-201"));
    }

    @Test
    @DisplayName("Поиск пользователей по группе без студентов")
    void testFindAllByGroup_EmptyGroup() {
        StudentGroup emptyGroup = new StudentGroup();
        emptyGroup.setName("ТИ-301");
        emptyGroup = studentGroupRepository.save(emptyGroup);
        
        personRepository.save(testStudent1);
        entityManager.flush();
        entityManager.clear();

        List<Person> studentsInEmptyGroup = personRepository.findAllByGroup(emptyGroup);

        assertThat(studentsInEmptyGroup).isEmpty();
    }

    @Test
    @DisplayName("Удаление пользователя")
    void testDeletePerson() {
        Person savedPerson = personRepository.save(testStudent1);
        Long personId = savedPerson.getId();
        entityManager.flush();
        entityManager.clear();

        personRepository.deleteById(personId);
        entityManager.flush();
        entityManager.clear();

        Person deletedPerson = personRepository.findById(personId).orElse(null);
        assertThat(deletedPerson).isNull();
    }

    @Test
    @DisplayName("Подсчет всех пользователей")
    void testCount() {
        personRepository.save(testStudent1);
        personRepository.save(testStudent2);
        personRepository.save(testTeacher);
        entityManager.flush();

        long totalCount = personRepository.count();

        assertThat(totalCount).isEqualTo(3);
    }

    @Test
    @DisplayName("Проверка существования пользователя")
    void testExistsById() {
        Person savedPerson = personRepository.save(testStudent1);
        entityManager.flush();

        boolean exists = personRepository.existsById(savedPerson.getId());
        boolean notExists = personRepository.existsById(999L);

        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }
} 