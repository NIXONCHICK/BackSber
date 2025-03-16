package back.controllers;

import back.scheduler.dto.ScheduleResponse;
import back.scheduler.service.SemesterSchedulerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
@Slf4j
public class SemesterScheduleController {

    private final SemesterSchedulerService semesterSchedulerService;

    @GetMapping("/semester")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScheduleResponse> generateSemesterSchedule(
            @RequestParam int year,
            @RequestParam int month,
            HttpServletRequest request) {
        
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            log.warn("User ID not found in request for semester schedule");
            return ResponseEntity.badRequest().body(
                new ScheduleResponse(null, "ID пользователя не найден в запросе")
            );
        }
        
        try {
            log.info("Generating semester schedule for user {}, year: {}, month: {}", userId, year, month);
            ScheduleResponse scheduleResponse = semesterSchedulerService.generateSemesterSchedule(userId, year, month);
            return ResponseEntity.ok(scheduleResponse);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameters for semester schedule: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                new ScheduleResponse(null, "Неверные параметры: " + e.getMessage())
            );
        } catch (Exception e) {
            log.error("Error generating semester schedule", e);
            return ResponseEntity.internalServerError().body(
                new ScheduleResponse(null, "Ошибка при генерации плана: " + e.getMessage())
            );
        }
    }
} 