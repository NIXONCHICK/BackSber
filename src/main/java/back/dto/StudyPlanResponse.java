package back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyPlanResponse {
    
    private String message;
    
    @Builder.Default
    private List<DailyPlan> dailyPlans = new ArrayList<>();
    
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    
    private int totalTasks;
    private int plannedTasks;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPlan {
        private LocalDate date;
        private List<TaskPlan> tasks;
        private int totalMinutes;
        private int maxMinutes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskPlan {
        private Long taskId;
        private String taskName;
        private String subjectName;
        private java.util.Date deadline;
        private int durationMinutes;
    }
} 