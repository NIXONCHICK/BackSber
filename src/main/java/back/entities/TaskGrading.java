package back.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Data
@Entity
@Table(name = "task_grading")
@AllArgsConstructor
@NoArgsConstructor
public class TaskGrading {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne
  @JoinColumns({
    @JoinColumn(name = "task_id", referencedColumnName = "task_id"),
    @JoinColumn(name = "person_id", referencedColumnName = "person_id")
  })
  private StudentTaskAssignment assignment;

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