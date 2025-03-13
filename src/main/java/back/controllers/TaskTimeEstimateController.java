package back.controllers;

import back.dto.TaskTimeEstimateResponse;
import back.entities.Person;
import back.services.OpenRouterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks/time-estimate")
@RequiredArgsConstructor
@Slf4j
public class TaskTimeEstimateController {

    private final OpenRouterService openRouterService;
    
    @GetMapping("/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskTimeEstimateResponse> getTaskTimeEstimate(@PathVariable Long taskId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            log.info("Получен запрос на оценку времени для задания {}, аутентификация: {}", taskId, auth);
            
            if (auth == null) {
                log.warn("Аутентификация отсутствует для запроса на оценку времени задания {}", taskId);
                return ResponseEntity.status(401).build();
            }
            
            if (!auth.isAuthenticated()) {
                log.warn("Пользователь не аутентифицирован для запроса на оценку времени задания {}", taskId);
                return ResponseEntity.status(401).build();
            }
            
            log.info("Пользователь аутентифицирован: {}, роли: {}", auth.getName(), auth.getAuthorities());
            
            Long userId = null;
            if (auth.getPrincipal() instanceof Person) {
                userId = ((Person) auth.getPrincipal()).getId();
                log.info("ID пользователя из Person: {}", userId);
            } else {
                log.warn("Не удалось получить ID пользователя из Person: {}", auth.getPrincipal().getClass().getName());
            }
            
            TaskTimeEstimateResponse response = openRouterService.getTaskTimeEstimate(taskId, userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении оценки времени для задания {}: {}", taskId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/{taskId}/refresh")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<TaskTimeEstimateResponse> refreshTaskTimeEstimate(@PathVariable Long taskId) {
        try {
            log.info("Получен запрос на обновление оценки времени для задания {}", taskId);
            
            Long userId = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Person) {
                userId = ((Person) auth.getPrincipal()).getId();
                log.info("ID пользователя из Person для обновления: {}", userId);
            }
            
            TaskTimeEstimateResponse response = openRouterService.refreshTaskTimeEstimate(taskId, userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при обновлении оценки времени для задания {}: {}", taskId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
} 