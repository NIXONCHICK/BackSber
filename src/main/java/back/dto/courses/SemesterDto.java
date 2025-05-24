package back.dto.courses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime; // Хотя здесь строка, может пригодиться для комментариев или будущей типизации

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemesterDto {
    private String id; // Может быть строковым представлением даты начала семестра, например "2023-09-01"
    private String name; // Например, "Осень 2023-2024"
    private String lastAiRefreshTimestamp; // Новое поле, строковое представление LocalDateTime
} 