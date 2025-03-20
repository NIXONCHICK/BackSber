package back.controllers;

import back.dto.StudyPlanResponse;
import back.entities.Person;
import back.scheduler.service.StudyPlannerService;
import back.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/study-plan")
@RequiredArgsConstructor
@Slf4j
public class StudyPlanController {
    
    private final StudyPlannerService studyPlannerService;
    private final EmailService emailService;
    

    @GetMapping("/semester")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StudyPlanResponse> createSemesterStudyPlan(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        try {
            log.info("Получен запрос на создание плана обучения на семестр для даты {}", date);
            
            Person currentUser = getCurrentUser();
            if (currentUser == null) {
                log.warn("Не удалось получить информацию о текущем пользователе");
                return ResponseEntity.status(401).build();
            }
            
            StudyPlanResponse response = studyPlannerService.createStudyPlan(currentUser, date);
            
            if (currentUser.getEmail() != null) {
                log.info("Отправка плана обучения на почту: {}", currentUser.getEmail());
                emailService.sendStudyPlanNotification(
                    currentUser.getEmail(),
                    response,
                    date
                );
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Ошибка при создании плана обучения: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    

    @GetMapping("/custom")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StudyPlanResponse> createCustomStudyPlan(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        try {
            log.info("Получен запрос на создание плана обучения на период с {} по {}", startDate, endDate);
            
            if (startDate.isAfter(endDate)) {
                log.warn("Дата начала {} после даты окончания {}", startDate, endDate);
                return ResponseEntity.badRequest().body(
                        StudyPlanResponse.builder()
                                .message("Дата начала не может быть позже даты окончания")
                                .build()
                );
            }
            
            Person currentUser = getCurrentUser();
            if (currentUser == null) {
                log.warn("Не удалось получить информацию о текущем пользователе");
                return ResponseEntity.status(401).build();
            }
            
            StudyPlanResponse response = studyPlannerService.createStudyPlan(currentUser, startDate, endDate);
            
            if (currentUser.getEmail() != null) {
                log.info("Отправка плана обучения на период на почту: {}", currentUser.getEmail());
                emailService.sendStudyPlanNotification(
                    currentUser.getEmail(),
                    response,
                    startDate
                );
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Ошибка при создании плана обучения: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    

    private Person getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Person) {
            return (Person) auth.getPrincipal();
        }
        return null;
    }
} 