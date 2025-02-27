package back.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Data
@Entity
@Table(name = "task_submission")
@AllArgsConstructor
@NoArgsConstructor
public class TaskSubmission {

  @EmbeddedId
  private TaskSubmissionId id;

  @ManyToOne
  @MapsId("taskId")
  @JoinColumn(name = "task_id")
  private Task task;

  @ManyToOne
  @MapsId("personId")
  @JoinColumn(name = "person_id")
  private Person person;

  @Column(name = "mark")
  private Float mark;

  @Column(name = "max_mark")
  private Float maxMark;

  @Column(name = "submission_date")
  private Date submissionDate;

  @Column(name = "submission_status")
  private String submissionStatus;

  @Column(name = "grading_status")
  private String gradingStatus;
}
