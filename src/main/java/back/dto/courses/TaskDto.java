package back.dto.courses;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TaskDto {
    private Long id;
    private String name;
    private String deadline;
    private String status;
    private String grade;
    private String description;
    private Integer estimatedMinutes;
    private String timeEstimateExplanation;

    public TaskDto(Long id, String name, String deadline, String status, String grade, String description, Integer estimatedMinutes, String timeEstimateExplanation) {
        this.id = id;
        this.name = name;
        this.deadline = deadline;
        this.status = status;
        this.grade = grade;
        this.description = description;
        this.estimatedMinutes = estimatedMinutes;
        this.timeEstimateExplanation = timeEstimateExplanation;
    }
} 