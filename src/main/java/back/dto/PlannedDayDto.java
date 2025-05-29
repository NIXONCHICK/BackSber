package back.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class PlannedDayDto {
    private int dayNumber;
    private LocalDate date;
    private int totalMinutesScheduledThisDay;
    private List<PlannedTaskDto> tasks;
} 