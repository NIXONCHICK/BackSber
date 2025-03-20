package back.config;

import back.scheduler.domain.SemesterSchedule;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class OptaPlannerConfig {

    @Bean
    public SolverFactory<SemesterSchedule> semesterScheduleSolverFactory() {
        SolverConfig solverConfig = new SolverConfig()
                .withEntityClasses(back.scheduler.domain.SemesterAssignment.class)
                .withSolutionClass(SemesterSchedule.class)
                .withConstraintProviderClass(back.scheduler.constraint.SemesterScheduleConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                    .withSecondsSpentLimit(300L));
        
        return SolverFactory.create(solverConfig);
    }

    @Bean
    public SolverManager<SemesterSchedule, Long> semesterScheduleSolverManager(
            SolverFactory<SemesterSchedule> solverFactory) {
        return SolverManager.create(solverFactory);
    }
} 