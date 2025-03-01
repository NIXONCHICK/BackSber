package back.repositories;

import back.entities.TaskGrading;
import back.entities.StudentTaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskGradingRepository extends JpaRepository<TaskGrading, Long> {
  TaskGrading findByAssignment(StudentTaskAssignment assignment);
}