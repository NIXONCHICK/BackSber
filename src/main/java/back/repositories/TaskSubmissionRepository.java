package back.repositories;

import back.entities.TaskSubmission;
import back.entities.TaskSubmissionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, TaskSubmissionId> {
}
