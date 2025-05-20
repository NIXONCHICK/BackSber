package back.repositories;

import back.entities.Enrollment;
import back.entities.EnrollmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.Date;

public interface EnrollmentRepository extends JpaRepository<Enrollment, EnrollmentId> {
  List<Enrollment> findAllByPersonId(Long personId);

  List<Enrollment> findAllByPersonIdAndSubject_SemesterDate(Long personId, java.sql.Date semesterDate);
}
