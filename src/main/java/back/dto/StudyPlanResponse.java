package back.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class StudyPlanResponse {
    private LocalDate semesterStartDate;
    private List<PlannedDayDto> plannedDays;
    private List<StudyPlanWarningDto> warnings;
    private int totalTasksConsideredForPlanning;
} 