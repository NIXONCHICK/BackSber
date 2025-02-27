package back.repositories;

import back.entities.Task;
import org.springframework.data.jpa.repository.JpaRepository;


public interface TaskRepository extends JpaRepository<Task, Long> {
  Task findByAssignmentsUrl(String assignmentsUrl);
}
