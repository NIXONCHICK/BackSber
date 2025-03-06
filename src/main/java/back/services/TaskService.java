package back.services;

import back.dto.TaskRequest;
import back.dto.TaskResponse;
import back.entities.*;
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
  private final EmailService emailService;

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
    Optional<Task> taskOptional = taskRepository.findTaskByIdForUser(taskId, userId);
    
    if (taskOptional.isEmpty()) {
      return Optional.empty();
    }
    
    Task task = taskOptional.get();
    
    if (task.getSource() != TaskSource.USER) {
      throw new RuntimeException("Можно изменять только задания, созданные пользователем");
    }
    
    if (!task.getName().equals(taskRequest.getName()) ||
        !task.getDeadline().equals(taskRequest.getDeadline())) {
      
      Optional<Task> duplicateTask = taskRepository.findDuplicateUserTask(
          taskRequest.getName(),
          taskRequest.getDeadline(),
          taskRequest.getDescription(),
          userId,
          TaskSource.USER
      );
      
      if (duplicateTask.isPresent() && !duplicateTask.get().getId().equals(taskId)) {
        throw new DuplicateTaskException("У вас уже существует задание с таким названием и дедлайном");
      }
    }
    
    task.setName(taskRequest.getName());
    task.setDeadline(taskRequest.getDeadline());
    task.setDescription(taskRequest.getDescription());
    
    Task updatedTask = taskRepository.save(task);
    
    return Optional.of(convertToTaskDeadlineResponse(updatedTask));
  }

  @Transactional
  public boolean deleteUserTask(Long taskId, Long userId) {
    Optional<Task> taskOptional = taskRepository.findTaskByIdForUser(taskId, userId);
    
    if (taskOptional.isEmpty()) {
      return false;
    }
    
    Task task = taskOptional.get();
    
    if (task.getSource() != TaskSource.USER) {
      throw new RuntimeException("Можно удалять только задания, созданные пользователем");
    }
    
    StudentTaskAssignmentId assignmentId = new StudentTaskAssignmentId(taskId, userId);
    studentTaskAssignmentRepository.deleteById(assignmentId);
    
    taskRepository.delete(task);
    
    return true;
  }

  @Transactional
  public TaskResponse createElderTask(TaskRequest taskRequest, Long elderId) {
    Person elder = personRepository.findById(elderId)
        .orElseThrow(() -> new RuntimeException("Староста не найден"));

    if (elder.getRole() != Role.ELDER) {
      throw new RuntimeException("Только староста может создавать задания для группы");
    }

    StudentGroup group = elder.getGroup();
    if (group == null) {
      throw new RuntimeException("Староста не привязан к группе");
    }

    Optional<Task> duplicateTask = taskRepository.findDuplicateElderTask(
        taskRequest.getName(),
        taskRequest.getDeadline(),
        taskRequest.getDescription(),
        group.getId(),
        TaskSource.ELDER
    );
    
    if (duplicateTask.isPresent()) {
        throw new DuplicateTaskException("В вашей группе уже существует задание с таким названием и дедлайном");
    }

    Task task = new Task();
    task.setName(taskRequest.getName());
    task.setDeadline(taskRequest.getDeadline());
    task.setDescription(taskRequest.getDescription());
    task.setSource(TaskSource.ELDER);

    Task savedTask = taskRepository.save(task);

    List<Person> groupStudents = personRepository.findAllByGroup(group);

    for (Person student : groupStudents) {
      StudentTaskAssignmentId assignmentId = new StudentTaskAssignmentId(savedTask.getId(), student.getId());
      StudentTaskAssignment assignment = new StudentTaskAssignment();
      assignment.setId(assignmentId);
      assignment.setTask(savedTask);
      assignment.setPerson(student);
      studentTaskAssignmentRepository.save(assignment);

      emailService.sendTaskNotification(
          student.getEmail(),
          savedTask.getName(),
          savedTask.getDescription(),
          savedTask.getDeadline(),
          group.getName(),
          student.getId().equals(elderId)
      );
    }

    return convertToTaskDeadlineResponse(savedTask);
  }

  @Transactional
  public Optional<TaskResponse> updateElderTask(Long taskId, TaskRequest taskRequest, Long elderId) {
    Person elder = personRepository.findById(elderId)
        .orElseThrow(() -> new RuntimeException("Староста не найден"));

    if (elder.getRole() != Role.ELDER) {
      throw new RuntimeException("Только староста может обновлять задания группы");
    }

    Optional<Task> taskOptional = taskRepository.findById(taskId);
    if (taskOptional.isEmpty()) {
      return Optional.empty();
    }

    Task task = taskOptional.get();
    if (task.getSource() != TaskSource.ELDER) {
      throw new RuntimeException("Можно редактировать только задания, созданные старостой");
    }

    List<StudentTaskAssignment> assignments = studentTaskAssignmentRepository.findAllByTaskId(taskId);
    if (assignments.isEmpty() || assignments.get(0).getPerson().getGroup().getId() != elder.getGroup().getId()) {
      throw new RuntimeException("Староста может редактировать только задания своей группы");
    }

    if (!task.getName().equals(taskRequest.getName()) ||
        !task.getDeadline().equals(taskRequest.getDeadline())) {
      
      Optional<Task> duplicateTask = taskRepository.findDuplicateElderTask(
          taskRequest.getName(),
          taskRequest.getDeadline(),
          taskRequest.getDescription(),
          elder.getGroup().getId(),
          TaskSource.ELDER
      );
      
      if (duplicateTask.isPresent() && !duplicateTask.get().getId().equals(taskId)) {
        throw new DuplicateTaskException("В вашей группе уже существует задание с таким названием и дедлайном");
      }
    }

    task.setName(taskRequest.getName());
    task.setDeadline(taskRequest.getDeadline());
    task.setDescription(taskRequest.getDescription());

    Task updatedTask = taskRepository.save(task);
    return Optional.of(convertToTaskDeadlineResponse(updatedTask));
  }

  @Transactional
  public boolean deleteElderTask(Long taskId, Long elderId) {
    Person elder = personRepository.findById(elderId)
        .orElseThrow(() -> new RuntimeException("Староста не найден"));

    if (elder.getRole() != Role.ELDER) {
      throw new RuntimeException("Только староста может удалять задания группы");
    }

    Optional<Task> taskOptional = taskRepository.findById(taskId);
    if (taskOptional.isEmpty()) {
      return false;
    }

    Task task = taskOptional.get();
    if (task.getSource() != TaskSource.ELDER) {
      throw new RuntimeException("Можно удалять только задания, созданные старостой");
    }

    List<StudentTaskAssignment> assignments = studentTaskAssignmentRepository.findAllByTaskId(taskId);
    if (assignments.isEmpty() || assignments.get(0).getPerson().getGroup().getId() != elder.getGroup().getId()) {
      throw new RuntimeException("Староста может удалять только задания своей группы");
    }

    studentTaskAssignmentRepository.deleteAllByTaskId(taskId);
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