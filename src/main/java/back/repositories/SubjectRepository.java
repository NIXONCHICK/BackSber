package back.repositories;

import back.entities.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.sql.Date;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
  List<Subject> findAllByAssignmentsUrlIn(Collection<String> urls);

  List<Subject> findAllBySemesterDate(Date semesterDate);

}
