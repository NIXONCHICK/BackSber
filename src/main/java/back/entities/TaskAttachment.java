package back.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "task_attachment")
@AllArgsConstructor
@NoArgsConstructor
public class TaskAttachment {

  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "task_id")
  private Task task;

  @Column(name = "file_name")
  private String fileName;

  @Column(name = "file_extension")
  private String fileExtension;

  @Column(name = "file_url", length = 1024)
  private String fileUrl;
}