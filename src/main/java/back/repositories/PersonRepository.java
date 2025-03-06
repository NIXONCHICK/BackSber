package back.repositories;

import back.entities.Person;
import back.entities.StudentGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonRepository extends JpaRepository<Person, Long> {
  Person findByEmail(String email);
  List<Person> findAllByGroup(StudentGroup group);
}
