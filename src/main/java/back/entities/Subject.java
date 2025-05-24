package back.entities;

import jakarta.persistence.*;
import lombok.*;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "subject")
@AllArgsConstructor
@NoArgsConstructor
public class Subject {
  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name")
  private String name;

  @Column(name = "assignments_url")
  private String assignmentsUrl;

  @Column(name = "semester_date")
  private Date semesterDate;

  @Column(name = "last_ai_refresh_timestamp")
  private LocalDateTime lastAiRefreshTimestamp;

  @OneToMany(mappedBy = "subject", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Enrollment> enrollments;
}