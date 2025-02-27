package back.repositories;

import back.entities.Enrollment;
import back.entities.EnrollmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, EnrollmentId> {
  // Для массового получения записей enrollment для определённого пользователя
  List<Enrollment> findAllByPersonId(Long personId);

  // Если нужна проверка наличия enrollment для конкретного предмета
  Enrollment findByPersonIdAndSubjectId(Long personId, Long subjectId);
}
