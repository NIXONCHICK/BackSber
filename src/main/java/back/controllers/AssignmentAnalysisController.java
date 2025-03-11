package back.controllers;

import back.dto.AssignmentAnalysisResult;
import back.entities.Person;
import back.repositories.PersonRepository;
import back.services.MoodleAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentAnalysisController {

    private final MoodleAssignmentService moodleAssignmentService;
    private final PersonRepository personRepository;
    

    @GetMapping("/analyze/{assignmentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AssignmentAnalysisResult> analyzeAssignment(@PathVariable Long assignmentId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        
        Person person = personRepository.findByEmail(email);
        if (person == null) {
            throw new RuntimeException("Пользователь не найден");
        }
        
        AssignmentAnalysisResult result = moodleAssignmentService.analyzeAssignment(assignmentId, person.getId());
        
        return ResponseEntity.ok(result);
    }
} 