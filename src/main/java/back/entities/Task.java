package back.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "task")
@AllArgsConstructor
@NoArgsConstructor
public class Task {
  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name")
  private String name;

  @Column(name = "deadline")
  private Date deadline;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Setter
  @Getter
  @Column(name = "estimated_minutes")
  private Integer estimatedMinutes;

  @Setter
  @Getter
  @Column(name = "time_estimate_explanation", columnDefinition = "TEXT")
  private String timeEstimateExplanation;

  @Setter
  @Getter
  @Column(name = "time_estimate_created_at")
  private Date timeEstimateCreatedAt;

  @Column(name = "assignments_url")
  private String assignmentsUrl;

  @Column(name = "source")
  private TaskSource source;

  @ManyToOne
  @JoinColumn(name = "subject_id")
  private Subject subject;

  @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TaskAttachment> attachments;

}