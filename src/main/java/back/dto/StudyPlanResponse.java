package back.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyPlanResponse {
    private LocalDate semesterStartDate;
    private LocalDate planStartDate;
    private List<PlannedDayDto> plannedDays;
    private List<StudyPlanWarningDto> warnings;
    private int totalTasksConsideredForPlanning;
} 