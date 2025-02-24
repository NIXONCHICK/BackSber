package back.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Formula;
import java.sql.Date;
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

  @OneToMany(mappedBy = "subject", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Enrollment> enrollments;

  @Column(name = "total_mark")
  @Formula("(SELECT COALESCE(SUM(e.mark), 0) FROM enrollment e WHERE e.subject_id = id)")
  private int totalMark;

  @Column(name = "total_max_mark")
  @Formula("(SELECT COALESCE(SUM(e.max_mark), 0) FROM enrollment e WHERE e.subject_id = id)")
  private int totalMaxMark;
}
