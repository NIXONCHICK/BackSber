package back.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudyPlanWarningDto {
    private Long taskId;
    private String taskName;
    private String message;
} 