package back.repositories;

import back.entities.Enrollment;
import back.entities.EnrollmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, EnrollmentId> {
  List<Enrollment> findAllByPersonId(Long personId);

  Enrollment findByPersonIdAndSubjectId(Long personId, Long subjectId);
}
