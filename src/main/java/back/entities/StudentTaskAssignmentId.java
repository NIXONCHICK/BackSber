package back.entities;

import jakarta.persistence.Embeddable;
import lombok.Data;
import java.io.Serializable;

@Data
@Embeddable
public class StudentTaskAssignmentId implements Serializable {
  private Long taskId;
  private Long personId;

  public StudentTaskAssignmentId() {}

  public StudentTaskAssignmentId(Long taskId, Long personId) {
    this.taskId = taskId;
    this.personId = personId;
  }
}