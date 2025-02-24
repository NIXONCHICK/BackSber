package back.repositories;

import back.entities.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
  Subject findByAssignmentsUrl(String assignmentsUrl);
}
