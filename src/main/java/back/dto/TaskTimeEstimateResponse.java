package back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskTimeEstimateResponse {
    
    private Long taskId;
    private String taskName;
    private Integer estimatedMinutes;
    private String explanation;
    private Date createdAt;
    private Boolean fromCache;
}