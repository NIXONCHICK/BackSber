package back.services;

import back.dto.TaskRequest;
import back.dto.TaskResponse;
import back.entities.Person;
import back.entities.StudentTaskAssignment;
import back.entities.StudentTaskAssignmentId;
import back.entities.Task;
import back.entities.TaskSource;
import back.exceptions.DuplicateTaskException;
import back.repositories.PersonRepository;
import back.repositories.StudentTaskAssignmentRepository;
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
  private final PersonRepository personRepository;
  private final StudentTaskAssignmentRepository studentTaskAssignmentRepository;

  public List<TaskResponse> getTasksWithDeadlinesForUser(Date date, Long userId) {
    List<Task> tasks = taskRepository.findTasksWithDeadlinesForUser(date, userId);
    return tasks.stream()
        .map(this::convertToTaskDeadlineResponse)
        .collect(Collectors.toList());
  }

  public Optional<TaskResponse> getTaskById(Long id) {
    return taskRepository.findById(id)
        .map(this::convertToTaskDeadlineResponse);
  }

  /**
   * Получение задачи по ID только для конкретного пользователя
   */
  public Optional<TaskResponse> getTaskByIdForUser(Long taskId, Long userId) {
    return taskRepository.findTaskByIdForUser(taskId, userId)
        .map(this::convertToTaskDeadlineResponse);
  }

  @Transactional
  public TaskResponse createUserTask(TaskRequest taskRequest, Long userId) {
    Person person = personRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    
    Optional<Task> duplicateTask = taskRepository.findDuplicateUserTask(
        taskRequest.getName(),
        taskRequest.getDeadline(),
        taskRequest.getDescription(),
        userId,
        TaskSource.USER
    );
    
    if (duplicateTask.isPresent()) {
        throw new DuplicateTaskException("У вас уже существует задание с таким названием и дедлайном");
    }
  
    Task task = new Task();
    task.setName(taskRequest.getName());
    task.setDeadline(taskRequest.getDeadline());
    task.setDescription(taskRequest.getDescription());
    task.setSource(TaskSource.USER);
    
    Task savedTask = taskRepository.save(task);
    
    StudentTaskAssignmentId assignmentId = new StudentTaskAssignmentId(savedTask.getId(), person.getId());
    StudentTaskAssignment assignment = new StudentTaskAssignment();
    assignment.setId(assignmentId);
    assignment.setTask(savedTask);
    assignment.setPerson(person);
    
    studentTaskAssignmentRepository.save(assignment);
    
    return convertToTaskDeadlineResponse(savedTask);
  }

  @Transactional
  public Optional<TaskResponse> updateUserTask(Long taskId, TaskRequest taskRequest, Long userId) {
    // Проверяем, что задание существует и принадлежит пользователю
    Optional<Task> taskOptional = taskRepository.findTaskByIdForUser(taskId, userId);
    
    if (taskOptional.isEmpty()) {
      return Optional.empty();
    }
    
    Task task = taskOptional.get();
    
    // Проверяем, что задание создано пользователем (источник USER)
    if (task.getSource() != TaskSource.USER) {
      throw new RuntimeException("Можно изменять только задания, созданные пользователем");
    }
    
    // Проверка на дубликаты (только если изменилось имя или дедлайн)
    if (!task.getName().equals(taskRequest.getName()) || 
        !task.getDeadline().equals(taskRequest.getDeadline())) {
      
      Optional<Task> duplicateTask = taskRepository.findDuplicateUserTask(
          taskRequest.getName(),
          taskRequest.getDeadline(),
          taskRequest.getDescription(),
          userId,
          TaskSource.USER
      );
      
      // Если существует другое задание с такими же параметрами
      if (duplicateTask.isPresent() && !duplicateTask.get().getId().equals(taskId)) {
        throw new DuplicateTaskException("У вас уже существует задание с таким названием и дедлайном");
      }
    }
    
    // Обновляем поля задания
    task.setName(taskRequest.getName());
    task.setDeadline(taskRequest.getDeadline());
    task.setDescription(taskRequest.getDescription());
    
    // Сохраняем обновленное задание
    Task updatedTask = taskRepository.save(task);
    
    return Optional.of(convertToTaskDeadlineResponse(updatedTask));
  }

  @Transactional
  public boolean deleteUserTask(Long taskId, Long userId) {
    // Проверяем, что задание существует и принадлежит пользователю
    Optional<Task> taskOptional = taskRepository.findTaskByIdForUser(taskId, userId);
    
    if (taskOptional.isEmpty()) {
      return false;
    }
    
    // Получаем задание
    Task task = taskOptional.get();
    
    // Проверяем, что задание создано пользователем (источник USER)
    if (task.getSource() != TaskSource.USER) {
      throw new RuntimeException("Можно удалять только задания, созданные пользователем");
    }
    
    // Находим соответствующую запись в StudentTaskAssignment
    StudentTaskAssignmentId assignmentId = new StudentTaskAssignmentId(taskId, userId);
    studentTaskAssignmentRepository.deleteById(assignmentId);
    
    // Удаляем само задание (оно гарантированно с источником USER)
    taskRepository.delete(task);
    
    return true;
  }

  private TaskResponse convertToTaskDeadlineResponse(Task task) {
    TaskResponse response = modelMapper.map(task, TaskResponse.class);

    if (task.getSubject() != null) {
      response.setSubject(task.getSubject().getName());
    }

    return response;
  }
}