package back.dto.courses;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TaskDto {
    private Long id;
    private String name;
    private String deadline; // Формат ГГГГ-ММ-ДД или null
    private String status;   // "Оценено", "Сдано", "Не сдано", "Зачет"
    private String grade;    // Оценка как строка или null
    private String description; // или null
    private Integer estimatedMinutes; // Оценка времени в минутах
    private String timeEstimateExplanation; // Объяснение оценки

    // Явно определенный конструктор
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