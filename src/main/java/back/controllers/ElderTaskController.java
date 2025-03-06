package back.controllers;

import back.dto.TaskRequest;
import back.dto.TaskResponse;
import back.entities.Person;
import back.entities.Role;
import back.repositories.PersonRepository;
import back.services.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/elder")
public class ElderTaskController {

    private final TaskService taskService;
    private final PersonRepository personRepository;

    @PostMapping("/tasks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createElderTask(
            @Valid @RequestBody TaskRequest taskRequest,
            HttpServletRequest request) {
        
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body("ID пользователя не найден в токене");
        }

        Person person = personRepository.findById(userId)
                .orElse(null);
        if (person == null) {
            return ResponseEntity.status(401).body("Пользователь не найден");
        }

        if (person.getRole() != Role.ELDER) {
            return ResponseEntity.status(403).body("Доступ запрещен: пользователь не является старостой");
        }
        
        try {
            TaskResponse createdTask = taskService.createElderTask(taskRequest, userId);
            return ResponseEntity.ok(createdTask);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/tasks/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateElderTask(
            @PathVariable Long taskId,
            @Valid @RequestBody TaskRequest taskRequest,
            HttpServletRequest request) {
        
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body("ID пользователя не найден в токене");
        }

        Person person = personRepository.findById(userId)
                .orElse(null);
        if (person == null) {
            return ResponseEntity.status(401).body("Пользователь не найден");
        }

        if (person.getRole() != Role.ELDER) {
            return ResponseEntity.status(403).body("Доступ запрещен: пользователь не является старостой");
        }
        
        try {
            return taskService.updateElderTask(taskId, taskRequest, userId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/tasks/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteElderTask(
            @PathVariable Long taskId,
            HttpServletRequest request) {
        
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body("ID пользователя не найден в токене");
        }

        Person person = personRepository.findById(userId)
                .orElse(null);
        if (person == null) {
            return ResponseEntity.status(401).body("Пользователь не найден");
        }

        if (person.getRole() != Role.ELDER) {
            return ResponseEntity.status(403).body("Доступ запрещен: пользователь не является старостой");
        }
        
        try {
            boolean deleted = taskService.deleteElderTask(taskId, userId);
            return deleted ? ResponseEntity.noContent().build() 
                         : ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
} 