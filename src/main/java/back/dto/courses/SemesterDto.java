package back.dto.courses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime; // Хотя здесь строка, может пригодиться для комментариев или будущей типизации

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemesterDto {
    private String id;
    private String name;
    private String lastAiRefreshTimestamp;
} 