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
    
    public StudyDay() {
    }
    
    public StudyDay(LocalDate date) {
        this.date = date;
    }
    
    public StudyDay(LocalDate date, int maxStudyMinutes) {
        this.date = date;
        this.maxStudyMinutes = maxStudyMinutes;
        this.availableMinutes = maxStudyMinutes;
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