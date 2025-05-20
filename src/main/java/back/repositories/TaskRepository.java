package back.repositories;

import back.entities.Task;
import back.entities.TaskSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Date;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TaskRepository extends JpaRepository<Task, Long> {
  List<Task> findAllByAssignmentsUrlIn(Collection<String> urls);
  List<Task> findAllBySubjectId(Long subjectId);

  @Query("SELECT t FROM Task t LEFT JOIN FETCH t.subject " +
      "JOIN StudentTaskAssignment sta ON sta.task = t " +
      "WHERE t.deadline >= :date " +
      "AND sta.person.id = :personId " +
      "ORDER BY t.deadline ASC")
  List<Task> findTasksWithDeadlinesForUser(@Param("date") java.util.Date date, @Param("personId") Long personId);
  
  @Query("SELECT t FROM Task t " +
      "JOIN StudentTaskAssignment sta ON sta.task = t " +
      "WHERE t.name = :name " +
      "AND t.deadline = :deadline " +
      "AND ((:description IS NULL AND t.description IS NULL) OR (:description IS NOT NULL AND t.description = :description)) " +
      "AND sta.person.id = :personId " +
      "AND t.source = :source")
  Optional<Task> findDuplicateUserTask(
      @Param("name") String name, 
      @Param("deadline") java.util.Date deadline, 
      @Param("description") String description, 
      @Param("personId") Long personId,
      @Param("source") TaskSource source);
      
  @Query("SELECT t FROM Task t LEFT JOIN FETCH t.subject " +
      "JOIN StudentTaskAssignment sta ON sta.task = t " +
      "WHERE t.id = :taskId " +
      "AND sta.person.id = :personId")
  Optional<Task> findTaskByIdForUser(
      @Param("taskId") Long taskId,
      @Param("personId") Long personId);

  @Query("SELECT DISTINCT t FROM Task t " +
      "JOIN StudentTaskAssignment sta ON sta.task = t " +
      "JOIN Person p ON sta.person = p " +
      "WHERE t.name = :name " +
      "AND t.deadline = :deadline " +
      "AND ((:description IS NULL AND t.description IS NULL) OR (:description IS NOT NULL AND t.description = :description)) " +
      "AND p.group.id = :groupId " +
      "AND t.source = :source")
  Optional<Task> findDuplicateElderTask(
      @Param("name") String name, 
      @Param("deadline") java.util.Date deadline, 
      @Param("description") String description, 
      @Param("groupId") Long groupId,
      @Param("source") TaskSource source);

  @Query("SELECT t FROM Task t " +
         "JOIN StudentTaskAssignment sta ON sta.task = t " +
         "WHERE t.source = :source " +
         "AND sta.person.id = :personId " +
         "AND t.subject.semesterDate = :semesterDate")
  List<Task> findTasksBySourceAndPersonIdAndSemesterDate(
          @Param("source") TaskSource source,
          @Param("personId") Long personId,
          @Param("semesterDate") Date semesterDate);

  @Query("SELECT t FROM Task t " +
         "JOIN StudentTaskAssignment sta ON sta.task = t " +
         "LEFT JOIN FETCH t.subject " +
         "WHERE sta.person.id = :personId")
  List<Task> findTasksByPersonId(@Param("personId") Long personId);
}