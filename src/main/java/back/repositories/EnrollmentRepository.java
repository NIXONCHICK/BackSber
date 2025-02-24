package back.repositories;

import back.entities.Enrollment;
import back.entities.EnrollmentId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, EnrollmentId> {
  Enrollment findByPersonIdAndSubjectId(Long personId, Long subjectId);
}
