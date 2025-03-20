package back.config;

import back.scheduler.constraint.TaskPartConstraintProvider;
import back.scheduler.domain.StudyAssignment;
import back.scheduler.domain.StudySchedule;
import back.scheduler.domain.TaskPart;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;


@Configuration
public class StudyPlannerConfig {

    @Bean
    public SolverFactory<StudySchedule> studyScheduleSolverFactory() {
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(StudySchedule.class)
                .withEntityClasses(StudyAssignment.class, TaskPart.class)
                .withConstraintProviderClass(TaskPartConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(Duration.ofSeconds(300)));
        

        
        return SolverFactory.create(solverConfig);
    }
    
    @Bean
    public SolverManager<StudySchedule, Long> studyScheduleSolverManager(
            SolverFactory<StudySchedule> solverFactory) {
        return SolverManager.create(solverFactory);
    }
} 