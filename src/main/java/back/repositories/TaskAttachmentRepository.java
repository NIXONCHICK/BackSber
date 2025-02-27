package back.repositories;

import back.entities.TaskAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
  List<TaskAttachment> findByTaskId(Long taskId);
  TaskAttachment findByTaskIdAndFileUrl(Long taskId, String fileUrl);
}