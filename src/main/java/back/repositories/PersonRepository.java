package back.repositories;

import back.entities.Person;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {
  Person findByEmail(String email);
}
