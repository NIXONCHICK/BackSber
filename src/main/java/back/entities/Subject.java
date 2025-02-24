package back.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

  @Column(name = "total_mark")
  @Formula("(SELECT COALESCE(SUM(t.mark), 0) FROM task t WHERE t.subject_id = id)")
  private int totalMark;

  @Column(name = "total_max_mark")
  @Formula("(SELECT COALESCE(SUM(t.max_mark), 0) FROM task t WHERE t.subject_id = id)")
  private int totalMaxMark;

  @Column(name = "assignments_url")
  private String assignmentsUrl;

  @Column(name = "semester_date")
  private Date semesterDate;

  @ManyToOne
  @JoinColumn(name = "person_id")
  private Person person;

  @OneToMany(mappedBy = "subject", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Task> tasks;
}
