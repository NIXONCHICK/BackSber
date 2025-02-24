package back.entities;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Formula;

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

  @Column(name = "total_mark", insertable = false, updatable = false)
  @Formula("(SELECT COALESCE(SUM(ts.mark), 0) FROM task_submission ts " +
      "JOIN task t ON ts.task_id = t.id " +
      "WHERE ts.person_id = person_id AND t.subject_id = subject_id)")
  private Float totalMark;

  @Column(name = "total_max_mark", insertable = false, updatable = false)
  @Formula("(SELECT COALESCE(SUM(ts.max_mark), 0) FROM task_submission ts " +
      "JOIN task t ON ts.task_id = t.id " +
      "WHERE ts.person_id = person_id AND t.subject_id = subject_id)")
  private Float totalMaxMark;
}
