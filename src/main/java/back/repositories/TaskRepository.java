package back.repositories;

import back.entities.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
  Task findByAssignmentsUrl(String assignmentsUrl);
}
