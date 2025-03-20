package back.controllers;

import back.dto.TaskTimeEstimateResponse;
import back.entities.Person;
import back.services.EmailService;
import back.services.OpenRouterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/tasks/time-estimate")
@RequiredArgsConstructor
@Slf4j
public class TaskTimeEstimateController {

    private final OpenRouterService openRouterService;
    private final EmailService emailService;
    
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
            Person person = null;
            if (auth.getPrincipal() instanceof Person) {
                person = (Person) auth.getPrincipal();
                userId = person.getId();
                log.info("ID пользователя из Person: {}", userId);
            } else {
                log.warn("Не удалось получить ID пользователя из Person: {}", auth.getPrincipal().getClass().getName());
            }
            
            TaskTimeEstimateResponse response = openRouterService.getTaskTimeEstimate(taskId, userId);
            
            // Отправляем электронное письмо с результатом
            if (person != null && person.getEmail() != null) {
                log.info("Отправка письма с оценкой времени на почту: {}", person.getEmail());
                emailService.sendTaskTimeEstimateNotification(
                    person.getEmail(),
                    response.getTaskName(),
                    response.getEstimatedMinutes(),
                    response.getExplanation()
                );
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении оценки времени для задания {}: {}", taskId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/{taskId}/refresh")
    public ResponseEntity<TaskTimeEstimateResponse> refreshTaskTimeEstimate(@PathVariable Long taskId) {
        try {
            log.info("Получен запрос на обновление оценки времени для задания {}", taskId);
            
            Long userId = null;
            Person person = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Person) {
                person = (Person) auth.getPrincipal();
                userId = person.getId();
                log.info("ID пользователя из Person для обновления: {}", userId);
            }
            
            TaskTimeEstimateResponse response = openRouterService.refreshTaskTimeEstimate(taskId, userId);
            
            // Отправляем электронное письмо с результатом
            if (person != null && person.getEmail() != null) {
                log.info("Отправка письма с обновленной оценкой времени на почту: {}", person.getEmail());
                emailService.sendTaskTimeEstimateNotification(
                    person.getEmail(),
                    response.getTaskName(),
                    response.getEstimatedMinutes(),
                    response.getExplanation()
                );
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при обновлении оценки времени для задания {}: {}", taskId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping("/semester")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TaskTimeEstimateResponse>> analyzeTasksBySemester(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date date) {
        try {
            log.info("Получен запрос на анализ заданий по семестру для даты: {}", date);
            
            Long userId;
            Person person;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Person) {
                person = (Person) auth.getPrincipal();
                userId = person.getId();
                log.info("ID пользователя из Person для анализа по семестру: {}", userId);
            } else {
                log.warn("Не удалось получить ID пользователя из Person для анализа по семестру");
                return ResponseEntity.status(401).build();
            }
            
            List<TaskTimeEstimateResponse> responses = openRouterService.analyzeTasksBySemester(date, userId);
            
            // Отправляем электронное письмо с результатом
            if (person.getEmail() != null && !responses.isEmpty()) {
                log.info("Отправка письма с анализом заданий по семестру на почту: {}", person.getEmail());
                emailService.sendSemesterTasksAnalysisNotification(
                    person.getEmail(),
                    responses,
                    date
                );
            }
            
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Ошибка при анализе заданий по семестру: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

}