package back.entities;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.Data;

@Data
@Embeddable
public class EnrollmentId implements Serializable {
  private Long personId;
  private Long subjectId;

  public EnrollmentId() {}

  public EnrollmentId(Long personId, Long subjectId) {
    this.personId = personId;
    this.subjectId = subjectId;
  }
}
