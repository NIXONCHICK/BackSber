package back.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.Date;

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

  @Column(name = "description")
  private String description;

  @Column(name = "assignments_url")
  private String assignmentsUrl;

  @ManyToOne
  @JoinColumn(name = "subject_id")
  private Subject subject;
}
