package back.repositories;

import back.entities.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
  List<Task> findAllByAssignmentsUrlIn(Collection<String> urls);

  Task findByAssignmentsUrl(String assignmentsUrl);
}
