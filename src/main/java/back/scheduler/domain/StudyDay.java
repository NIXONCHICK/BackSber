package back.scheduler.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class StudyDay {
    
    private LocalDate date;
    
    private int maxStudyMinutes = 180;
    
    private int availableMinutes = 180;

    public StudyDay(LocalDate date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "StudyDay{" +
                "date=" + date +
                ", maxStudyMinutes=" + maxStudyMinutes +
                ", availableMinutes=" + availableMinutes +
                '}';
    }
} 