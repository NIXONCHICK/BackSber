package back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskRequest {
    @NotBlank(message = "Название задания обязательно")
    private String name;
    
    @NotNull(message = "Дедлайн обязателен")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date deadline;
    
    private String description;
} 