package back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskDeadlineResponse {
  private String name;
  private Date deadline;
  private String description;
  private String subject;
}
