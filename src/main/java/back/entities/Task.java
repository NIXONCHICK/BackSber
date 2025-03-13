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

  @Column(name = "estimated_minutes")
  private Integer estimatedMinutes;

  @Column(name = "time_estimate_explanation", columnDefinition = "TEXT")
  private String timeEstimateExplanation;

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

  public Integer getEstimatedMinutes() {
    return estimatedMinutes;
  }

  public void setEstimatedMinutes(Integer estimatedMinutes) {
    this.estimatedMinutes = estimatedMinutes;
  }

  public String getTimeEstimateExplanation() {
    return timeEstimateExplanation;
  }

  public void setTimeEstimateExplanation(String timeEstimateExplanation) {
    this.timeEstimateExplanation = timeEstimateExplanation;
  }

  public Date getTimeEstimateCreatedAt() {
    return timeEstimateCreatedAt;
  }

  public void setTimeEstimateCreatedAt(Date timeEstimateCreatedAt) {
    this.timeEstimateCreatedAt = timeEstimateCreatedAt;
  }

  @Transient
  public String getFormattedEstimatedTime() {
    if (estimatedMinutes == null) {
      return "Не определено";
    }
    
    int hours = estimatedMinutes / 60;
    int minutes = estimatedMinutes % 60;
    
    if (hours > 0 && minutes > 0) {
      return hours + " ч " + minutes + " мин";
    } else if (hours > 0) {
      return hours + " ч";
    } else {
      return minutes + " мин";
    }
  }
}