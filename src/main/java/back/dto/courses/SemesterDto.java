package back.dto.courses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemesterDto {
    private String id;
    private String name;
    private String lastAiRefreshTimestamp;
} 