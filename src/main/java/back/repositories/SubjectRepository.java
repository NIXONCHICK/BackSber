package back.repositories;

import back.entities.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
  // Для массового получения предметов по их ссылкам
  List<Subject> findAllByAssignmentsUrlIn(Collection<String> urls);

  // Если нужно получить один предмет по ссылке
  Subject findByAssignmentsUrl(String assignmentsUrl);
}
