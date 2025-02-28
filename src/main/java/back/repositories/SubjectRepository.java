package back.repositories;

import back.entities.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
  List<Subject> findAllByAssignmentsUrlIn(Collection<String> urls);

  Subject findByAssignmentsUrl(String assignmentsUrl);
}
