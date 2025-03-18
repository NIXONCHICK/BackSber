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
    
    public StudySchedule(Long id, LocalDate semesterStartDate, LocalDate semesterEndDate, 
                        List<StudyDay> availableDays, List<Task> tasks, 
                        List<TaskChain> taskChains, List<TaskPart> taskParts) {
        this.id = id;
        this.semesterStartDate = semesterStartDate;
        this.semesterEndDate = semesterEndDate;
        this.availableDays = availableDays;
        this.tasks = tasks;
        this.taskChains = taskChains;
        this.taskParts = taskParts;
        this.assignments = new ArrayList<>();
    }
    
    public StudySchedule(Long id, LocalDate semesterStartDate, LocalDate semesterEndDate, 
                         List<StudyDay> availableDays, List<Task> tasks, 
                         List<StudyAssignment> assignments) {
        this.id = id;
        this.semesterStartDate = semesterStartDate;
        this.semesterEndDate = semesterEndDate;
        this.availableDays = availableDays;
        this.tasks = tasks;
        this.assignments = assignments;
        this.taskChains = new ArrayList<>();
        this.taskParts = new ArrayList<>();
    }
} 