package back.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "person")
@AllArgsConstructor
@NoArgsConstructor
public class Person {
  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name")
  private String name;

  @Column(name = "surname")
  private String surname;

  @Column(name = "patronymic")
  private String patronymic;

  @Email
  @Column(name = "email")
  private String email;

  @Column(name = "password")
  private String password;

  @Column(name = "role")
  private Role role;

  @ManyToOne
  @JoinColumn(name = "group_id", foreignKey = @ForeignKey(name = "fk_person_student_group"))
  @OnDelete(action = OnDeleteAction.SET_NULL)
  private StudentGroup group;

  @Column(name = "moodle_session")
  private String moodleSession;

  @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Subject> subjects;
}
