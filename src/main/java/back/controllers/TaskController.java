package back.controllers;

import back.dto.TaskRequest;
import back.dto.TaskResponse;
import back.services.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class TaskController {

  private final TaskService taskService;

  @GetMapping("/tasks/deadlines")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<TaskResponse>> getTasksWithDeadlines(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date date,
      HttpServletRequest request) {

    Long userId = (Long) request.getAttribute("userId");
    if (userId == null) {
      return ResponseEntity.badRequest().build();
    }
    
    Date effectiveDate = (date != null) ? date : new Date();
    List<TaskResponse> tasks = taskService.getTasksWithDeadlinesForUser(effectiveDate, userId);
    return ResponseEntity.ok(tasks);
  }

  @PostMapping("/tasks")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<TaskResponse> addTask(
      @Valid @RequestBody TaskRequest taskRequest,
      HttpServletRequest request) {
    
    Long userId = (Long) request.getAttribute("userId");
    if (userId == null) {
      return ResponseEntity.badRequest().build();
    }
    
    TaskResponse createdTask = taskService.createUserTask(taskRequest, userId);
    return ResponseEntity.ok(createdTask);
  }
  
  @GetMapping("/tasks/{taskId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<TaskResponse> getTaskById(
      @PathVariable Long taskId,
      HttpServletRequest request) {
    
    Long userId = (Long) request.getAttribute("userId");
    if (userId == null) {
      return ResponseEntity.badRequest().build();
    }
    
    return taskService.getTaskByIdForUser(taskId, userId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PutMapping("/tasks/{taskId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<TaskResponse> updateTask(
      @PathVariable Long taskId,
      @Valid @RequestBody TaskRequest taskRequest,
      HttpServletRequest request) {
    
    Long userId = (Long) request.getAttribute("userId");
    if (userId == null) {
      return ResponseEntity.badRequest().build();
    }
    
    return taskService.updateUserTask(taskId, taskRequest, userId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/tasks/{taskId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> deleteTask(
      @PathVariable Long taskId,
      HttpServletRequest request) {
    
    Long userId = (Long) request.getAttribute("userId");
    if (userId == null) {
      return ResponseEntity.badRequest().build();
    }
    
    boolean deleted = taskService.deleteUserTask(taskId, userId);
    
    if (deleted) {
      return ResponseEntity.noContent().build();
    } else {
      return ResponseEntity.notFound().build();
    }
  }
}