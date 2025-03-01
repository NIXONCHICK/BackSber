package back.repositories;

import back.entities.StudentTaskAssignment;
import back.entities.StudentTaskAssignmentId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentTaskAssignmentRepository extends JpaRepository<StudentTaskAssignment, StudentTaskAssignmentId> {
}