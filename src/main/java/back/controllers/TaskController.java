package back.controllers;

import back.dto.TaskDeadlineResponse;
import back.services.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class TaskController {

  private final TaskService taskService;

  @GetMapping("/tasks/deadlines")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<TaskDeadlineResponse>> getTasksWithDeadlines(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date date,
      HttpServletRequest request) {

    Long userId = (Long) request.getAttribute("userId");
    if (userId == null) {
      return ResponseEntity.badRequest().build();
    }
    
    Date effectiveDate = (date != null) ? date : new Date();
    List<TaskDeadlineResponse> tasks = taskService.getTasksWithDeadlinesForUser(effectiveDate, userId);
    return ResponseEntity.ok(tasks);
  }


}