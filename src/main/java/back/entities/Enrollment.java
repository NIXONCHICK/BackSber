package back.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "enrollment")
@AllArgsConstructor
@NoArgsConstructor
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

  @Column(name = "mark")
  private Float mark;

  @Column(name = "max_mark")
  private Float maxMark;
}
