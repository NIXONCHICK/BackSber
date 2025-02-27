package back.repositories;

import back.entities.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
  // Для массового получения заданий по ссылкам
  List<Task> findAllByAssignmentsUrlIn(Collection<String> urls);

  // Если нужно получить задание по ссылке
  Task findByAssignmentsUrl(String assignmentsUrl);
}
