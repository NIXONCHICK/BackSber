package back.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "student_group")
@AllArgsConstructor
@NoArgsConstructor
public class StudentGroup {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "name")
  private String name;

  @OneToMany(mappedBy = "group", cascade = CascadeType.ALL)
  private List<Person> persons;
}
