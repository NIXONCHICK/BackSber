package back.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "student_task_assignment")
@AllArgsConstructor
@NoArgsConstructor
public class StudentTaskAssignment {

  @EmbeddedId
  private StudentTaskAssignmentId id;

  @ManyToOne
  @MapsId("taskId")
  @JoinColumn(name = "task_id")
  private Task task;

  @ManyToOne
  @MapsId("personId")
  @JoinColumn(name = "person_id")
  private Person person;
}