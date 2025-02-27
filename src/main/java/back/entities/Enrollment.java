package back.entities;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "enrollment")
public class Enrollment {

  @EmbeddedId
  private EnrollmentId id;

  @ManyToOne
  @MapsId("personId")
  @JoinColumn(name = "person_id")
  private Person person;

  @ManyToOne
  @MapsId("subjectId")
  @JoinColumn(name = "subject_id")
  private Subject subject;
}