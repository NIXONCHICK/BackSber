package back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentAnalysisResult {
    private String assignmentName;
    private String courseName;
    private int totalTokens;
    private String extractedText;
    
    @Builder.Default
    private List<FileAnalysisDetail> files = new ArrayList<>();
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileAnalysisDetail {
        private String fileName;
        private String fileExtension;
        private int tokenCount;
        private String fileContent;
    }
} 