package back.controllers;

import back.dto.courses.SemesterDto;
import back.dto.courses.SubjectDto;
import back.dto.courses.TaskDto;
import back.entities.Person;
import back.services.CourseQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CourseController {

    private final CourseQueryService courseQueryService;

    @GetMapping("/semesters")
    public ResponseEntity<List<SemesterDto>> getUserSemesters(@AuthenticationPrincipal Person authenticatedPerson) {
        if (authenticatedPerson == null) {
            return ResponseEntity.status(401).build();
        }
        List<SemesterDto> semesters = courseQueryService.getUserSemesters(authenticatedPerson);
        return ResponseEntity.ok(semesters);
    }

    @GetMapping("/semesters/{semesterId}/subjects")
    public ResponseEntity<List<SubjectDto>> getSubjectsForSemester(
            @AuthenticationPrincipal Person authenticatedPerson,
            @PathVariable String semesterId) {
        if (authenticatedPerson == null) {
            return ResponseEntity.status(401).build();
        }
        List<SubjectDto> subjects = courseQueryService.getSubjectsForSemester(authenticatedPerson, semesterId);
        return ResponseEntity.ok(subjects);
    }

    @GetMapping("/subjects/{subjectId}/tasks")
    public ResponseEntity<List<TaskDto>> getTasksForSubject(
            @AuthenticationPrincipal Person authenticatedPerson,
            @PathVariable Long subjectId) {
        if (authenticatedPerson == null) {
            return ResponseEntity.status(401).build();
        }
        List<TaskDto> tasks = courseQueryService.getTasksForSubject(authenticatedPerson, subjectId);
        return ResponseEntity.ok(tasks);
    }
} 