package back.repositories;

import back.entities.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
  Subject findByAssignmentsUrl(String assignmentsUrl);

  @Query("SELECT s FROM Subject s JOIN s.enrollments e WHERE e.person.id = :personId")
  List<Subject> findByPersonId(@Param("personId") Long personId);
}
