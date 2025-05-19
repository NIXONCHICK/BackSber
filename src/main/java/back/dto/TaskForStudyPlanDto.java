package back.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime; // Используем LocalDateTime для дедлайна, как в PlannedTaskDto
import java.util.Date;

@Data
@Builder
public class TaskForStudyPlanDto {
    private Long id;
    private String name;
    private Date originalDeadline; // Сохраняем оригинальный Date дедлайн из Task entity
    private LocalDateTime deadlineForPlanning; // LocalDateTime для удобства в StudyPlanService
    private Integer estimatedMinutes;
    private String subjectName;
    private String status; // "Оценено", "Сдано", "Не сдано", "Зачет"
    // Можно добавить и другие поля из Task, если они понадобятся в StudyPlanService
} 