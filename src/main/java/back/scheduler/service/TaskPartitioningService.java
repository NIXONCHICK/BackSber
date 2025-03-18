package back.scheduler.service;

import back.entities.Task;
import back.scheduler.domain.TaskChain;
import back.scheduler.domain.TaskPart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@Slf4j
public class TaskPartitioningService {
    

    private static final int MAX_MINUTES_PER_DAY = 180;
    

    private static final int MIN_CHUNK_SIZE = 30;
    

    public List<TaskChain> createTaskChains(List<Task> tasks) {
        List<TaskChain> chains = new ArrayList<>();
        long chainId = 1;
        
        for (Task task : tasks) {
            TaskChain chain = new TaskChain(chainId++, task);
            chains.add(chain);
        }
        
        return chains;
    }
    

    public List<TaskPart> createTaskParts(List<TaskChain> chains) {
        List<TaskPart> parts = new ArrayList<>();
        Map<Long, List<TaskPart>> partsByChainId = new HashMap<>();
        long partId = 1;
        
        for (TaskChain chain : chains) {
            int totalDuration = chain.getTotalDurationMinutes();
            List<TaskPart> chainParts = new ArrayList<>();
            
            if (totalDuration <= MAX_MINUTES_PER_DAY) {
                TaskPart part = new TaskPart(partId++, 1, totalDuration);
                chainParts.add(part);
                parts.add(part);
                partsByChainId.put(chain.getId(), chainParts);
                continue;
            }
            
            int remainingDuration = totalDuration;
            int partIndex = 1;
            
            while (remainingDuration > 0) {
                int partDuration;
                
                if (remainingDuration <= MAX_MINUTES_PER_DAY) {
                    partDuration = remainingDuration;
                } else if (remainingDuration <= MAX_MINUTES_PER_DAY * 2) {
                    partDuration = remainingDuration / 2;
                } else {
                    partDuration = Math.max(
                        MIN_CHUNK_SIZE,
                        Math.min(MAX_MINUTES_PER_DAY, remainingDuration / ((remainingDuration + MAX_MINUTES_PER_DAY - 1) / MAX_MINUTES_PER_DAY))
                    );
                }
                
                TaskPart part = new TaskPart(partId++, partIndex++, partDuration);
                chainParts.add(part);
                parts.add(part);
                
                remainingDuration -= partDuration;
            }
            
            partsByChainId.put(chain.getId(), chainParts);
        }
        
        createTaskPartLinks(partsByChainId, chains);
        
        return parts;
    }
    

    private void createTaskPartLinks(Map<Long, List<TaskPart>> partsByChainId, List<TaskChain> chains) {
        log.info("Устанавливаем связи между частями заданий");
        
        for (TaskChain chain : chains) {
            List<TaskPart> chainParts = partsByChainId.get(chain.getId());
            if (chainParts == null || chainParts.isEmpty()) {
                continue;
            }
            
            chainParts.get(0).setPreviousTaskStep(chain);
            
            for (int i = 1; i < chainParts.size(); i++) {
                TaskPart current = chainParts.get(i);
                TaskPart previous = chainParts.get(i - 1);
                
                current.setPreviousTaskStep(previous);
                previous.setNextTaskPart(current);
            }
            
            log.debug("Установлены связи для цепи {} с {} частями", chain.getId(), chainParts.size());
        }
        
        log.info("Связи между частями заданий установлены");
    }
    

    public boolean requiresPartitioning(Task task) {
        Integer estimatedMinutes = task.getEstimatedMinutes();
        return estimatedMinutes != null && estimatedMinutes > MAX_MINUTES_PER_DAY;
    }
} 