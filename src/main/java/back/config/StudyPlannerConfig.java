package back.config;

import back.scheduler.constraint.TaskPartConstraintProvider;
import back.scheduler.domain.StudyAssignment;
import back.scheduler.domain.StudySchedule;
import back.scheduler.domain.TaskPart;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.optaplanner.core.config.heuristic.selector.move.MoveSelectorConfig;
import org.optaplanner.core.config.heuristic.selector.move.factory.MoveListFactoryConfig;
import org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicType;
import org.optaplanner.core.config.localsearch.LocalSearchType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Конфигурация OptaPlanner для планировщика учебы
 */
@Configuration
public class StudyPlannerConfig {

    @Bean
    public SolverFactory<StudySchedule> studyScheduleSolverFactory() {
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(StudySchedule.class)
                .withEntityClasses(StudyAssignment.class, TaskPart.class)
                .withConstraintProviderClass(TaskPartConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(Duration.ofSeconds(300))); // Ограничение времени решения - 5 минут
        
        // В комментариях оставляем рекомендации по улучшению настроек
        // Для более сложных настроек: 
        // 1. Использовать FIRST_FIT_DECREASING как стратегию конструкции
        // 2. Использовать TABU_SEARCH для локального поиска
        // Требует дополнительной настройки через XML или более сложную конфигурацию
        
        return SolverFactory.create(solverConfig);
    }
    
    @Bean
    public SolverManager<StudySchedule, Long> studyScheduleSolverManager(
            SolverFactory<StudySchedule> solverFactory) {
        return SolverManager.create(solverFactory);
    }
} 