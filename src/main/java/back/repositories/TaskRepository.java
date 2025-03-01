package back.repositories;

import back.entities.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
  List<Task> findAllByAssignmentsUrlIn(Collection<String> urls);

  @Query("SELECT t FROM Task t JOIN FETCH t.subject WHERE t.deadline >= :date ORDER BY t.deadline ASC")
  List<Task> findTasksWithDeadlinesOnOrAfterDate(@Param("date") Date date);
}
