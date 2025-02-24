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

  @Column(name = "is_done")
  private boolean isDone;

  @Column(name = "is_uploaded")
  private boolean isUploaded;

  @Column(name = "mark")
  private float mark;

  @Column(name = "max_mark")
  private float maxMark;

  @ManyToOne
  @JoinColumn(name = "subject_id")
  private Subject subject;
}
