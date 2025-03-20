package back.scheduler.domain;

import lombok.Getter;
import lombok.Setter;
import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.PlanningScore;
import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.ProblemFactCollectionProperty;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@PlanningSolution
@Getter
@Setter
public class SemesterSchedule {

    private LocalDate semesterStart;
    private LocalDate semesterEnd;
    private Long userId;

    @PlanningEntityCollectionProperty
    private List<SemesterAssignment> assignments;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "availableDays")
    private List<LocalDate> availableDays;

    @PlanningScore
    private HardMediumSoftScore score;

    public SemesterSchedule() {
    }

    public SemesterSchedule(LocalDate semesterStart, LocalDate semesterEnd, Long userId,
                          List<SemesterAssignment> assignments) {
        this.semesterStart = semesterStart;
        this.semesterEnd = semesterEnd;
        this.userId = userId;
        this.assignments = assignments;
        this.availableDays = generateAvailableDays();
    }

    private List<LocalDate> generateAvailableDays() {
        return semesterStart.datesUntil(semesterEnd.plusDays(1))
                .filter(date -> date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY)
                .toList();
    }
} 