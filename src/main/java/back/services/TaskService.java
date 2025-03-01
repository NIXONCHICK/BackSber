package back.services;

import back.dto.TaskDeadlineResponse;
import back.entities.Task;
import back.repositories.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {

  private final TaskRepository taskRepository;
  private final ModelMapper modelMapper;

  public List<TaskDeadlineResponse> getTasksWithDeadlinesOnOrAfterDate(Date date) {
    List<Task> tasks = taskRepository.findTasksWithDeadlinesOnOrAfterDate(date);
    return tasks.stream()
        .map(this::convertToTaskDeadlineResponse)
        .collect(Collectors.toList());
  }
  
  public List<TaskDeadlineResponse> getTasksWithDeadlinesForUser(Date date, Long userId) {
    List<Task> tasks = taskRepository.findTasksWithDeadlinesForUser(date, userId);
    return tasks.stream()
        .map(this::convertToTaskDeadlineResponse)
        .collect(Collectors.toList());
  }

  public Optional<TaskDeadlineResponse> getTaskById(Long id) {
    return taskRepository.findById(id)
        .map(this::convertToTaskDeadlineResponse);
  }

  private TaskDeadlineResponse convertToTaskDeadlineResponse(Task task) {
    TaskDeadlineResponse response = modelMapper.map(task, TaskDeadlineResponse.class);

    if (task.getSubject() != null) {
      response.setSubject(task.getSubject().getName());
    }

    return response;
  }
}