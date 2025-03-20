package back.scheduler.domain;

import back.entities.Task;
import lombok.Getter;
import lombok.Setter;
import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.PlanningScore;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.ProblemFactCollectionProperty;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@PlanningSolution
@Getter
@Setter
public class StudySchedule {
    
    private Long id;
    
    private LocalDate semesterStartDate;
    private LocalDate semesterEndDate;
    
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "availableDays")
    private List<StudyDay> availableDays;
    
    @ProblemFactCollectionProperty
    private List<Task> tasks;
    
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "taskStepRange")
    private List<TaskChain> taskChains;
    
    @PlanningEntityCollectionProperty
    private List<TaskPart> taskParts;
    
    @PlanningEntityCollectionProperty
    private List<StudyAssignment> assignments;
    
    @PlanningScore
    private HardMediumSoftScore score;
    
    private List<TaskTimeWarning> timeWarnings = new ArrayList<>();
    
    public StudySchedule() {
    }

}