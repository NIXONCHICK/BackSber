package back.repositories;

import back.entities.StudentTaskAssignment;
import back.entities.StudentTaskAssignmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentTaskAssignmentRepository extends JpaRepository<StudentTaskAssignment, StudentTaskAssignmentId> {
    List<StudentTaskAssignment> findAllByTaskId(Long taskId);
    
    @Modifying
    @Query("DELETE FROM StudentTaskAssignment sta WHERE sta.task.id = :taskId")
    void deleteAllByTaskId(@Param("taskId") Long taskId);
}