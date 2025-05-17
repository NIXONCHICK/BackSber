package back.dto.courses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskDto {
    private Long id;
    private String name;
    private String deadline; // Формат ГГГГ-ММ-ДД или null
    private String status;   // "Оценено", "Сдано", "Не сдано", "Зачет"
    private String grade;    // Оценка как строка или null
    private String description; // или null
} 