package back.services;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import back.dto.TaskTimeEstimateResponse;
import back.dto.StudyPlanResponse;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;

    @Async
    public void sendTaskNotification(String to, String taskName, String description, Date deadline, String groupName, boolean isElder) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(isElder ? "Подтверждение создания задания: " + taskName : "Новое задание: " + taskName);

            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
            String deadlineFormatted = dateFormat.format(deadline);

            String formattedDescription = description != null ?
                description.replace("\n", "<br>") : 
                "Описание отсутствует";

            String htmlMsg = String.format("""
                    <!DOCTYPE html>
                    <html lang="ru">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            /* Основные стили */
                            body {
                                font-family: 'Segoe UI', Arial, sans-serif;
                                line-height: 1.6;
                                color: #333;
                                margin: 0;
                                padding: 0;
                                background-color: #f5f5f5;
                            }
                            .container {
                                max-width: 600px;
                                margin: 20px auto;
                                background-color: #ffffff;
                                padding: 30px;
                                border-radius: 10px;
                                box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                            }
                            /* Заголовок */
                            .header {
                                color: #1a73e8;
                                font-size: 24px;
                                margin-bottom: 25px;
                                padding-bottom: 15px;
                                border-bottom: 2px solid #e0e0e0;
                                font-weight: 600;
                            }
                            /* Блок для старост */
                            .elder-note {
                                background-color: #e8f5e9;
                                padding: 15px 20px;
                                border-radius: 8px;
                                margin: 15px 0;
                                border-left: 4px solid #4caf50;
                                color: #2e7d32;
                            }
                            /* Информация о задании */
                            .task-info {
                                background-color: #f8f9fa;
                                padding: 25px;
                                border-radius: 8px;
                                margin: 20px 0;
                                border: 1px solid #e0e0e0;
                            }
                            .task-name {
                                color: #1a73e8;
                                font-size: 22px;
                                margin-bottom: 20px;
                                font-weight: bold;
                            }
                            .deadline {
                                background-color: #fef2f2;
                                color: #dc2626;
                                padding: 12px 15px;
                                border-radius: 6px;
                                margin: 15px 0;
                                font-weight: 500;
                                display: inline-block;
                            }
                            .description {
                                margin: 20px 0;
                                padding: 15px;
                                background-color: #ffffff;
                                border-radius: 6px;
                                border: 1px solid #e0e0e0;
                            }
                            .description-title {
                                color: #374151;
                                font-weight: 600;
                                margin-bottom: 10px;
                            }
                            /* Подвал */
                            .footer {
                                margin-top: 30px;
                                padding-top: 20px;
                                border-top: 1px solid #e0e0e0;
                                color: #6b7280;
                                font-size: 14px;
                            }
                            .footer p {
                                margin: 8px 0;
                            }
                            /* Адаптивность для мобильных устройств */
                            @media screen and (max-width: 600px) {
                                .container {
                                    padding: 15px;
                                    margin: 10px;
                                }
                                .task-info {
                                    padding: 15px;
                                }
                                .header {
                                    font-size: 20px;
                                }
                                .task-name {
                                    font-size: 18px;
                                }
                            }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                %s
                            </div>
                            %s
                            <div class="task-info">
                                <div class="task-name">
                                    %s
                                </div>
                                <div class="deadline">
                                    📅 Срок сдачи: %s
                                </div>
                                <div class="description">
                                    <div class="description-title">📝 Описание задания:</div>
                                    %s
                                </div>
                            </div>
                            <div class="footer">
                                <p>%s</p>
                                <p>💡 Для просмотра деталей задания войдите в систему.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """,
                isElder ? "Подтверждение создания задания для группы " + groupName : "Новое задание для группы " + groupName,
                isElder ? "<div class='elder-note'>✅ Вы успешно создали новое задание для своей группы.</div>" : "",
                taskName,
                deadlineFormatted,
                formattedDescription,
                isElder ? "Это подтверждение создания задания. Студенты группы получат уведомления." : "Это автоматическое уведомление. Пожалуйста, не отвечайте на него."
            );

            helper.setText(htmlMsg, true);
            mailSender.send(mimeMessage);

        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    @Async
    public void sendTaskTimeEstimateNotification(String to, String taskName, Integer estimatedMinutes, String explanation) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(to);
            
            if (taskName != null && taskName.trim().endsWith("Задание")) {
                taskName = taskName.substring(0, taskName.length() - 7).trim();
            }
            
            helper.setSubject("Оценка времени выполнения задания: " + taskName);

            String formattedExplanation = explanation != null ?
                explanation.replace("\n", "<br>") : 
                "Объяснение отсутствует";

            String formattedTime;
            if (estimatedMinutes >= 60) {
                int hours = estimatedMinutes / 60;
                int minutes = estimatedMinutes % 60;
                formattedTime = hours + " ч. " + (minutes > 0 ? minutes + " мин." : "");
            } else {
                formattedTime = estimatedMinutes + " мин.";
            }

            String htmlMsg = String.format("""
                    <!DOCTYPE html>
                    <html lang="ru">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            /* Основные стили */
                            body {
                                font-family: 'Segoe UI', Arial, sans-serif;
                                line-height: 1.6;
                                color: #333;
                                margin: 0;
                                padding: 0;
                                background-color: #f5f5f5;
                            }
                            .container {
                                max-width: 600px;
                                margin: 20px auto;
                                background-color: #ffffff;
                                padding: 30px;
                                border-radius: 10px;
                                box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                            }
                            /* Заголовок */
                            .header {
                                color: #1a73e8;
                                font-size: 24px;
                                margin-bottom: 25px;
                                padding-bottom: 15px;
                                border-bottom: 2px solid #e0e0e0;
                                font-weight: 600;
                            }
                            /* Информация о времени выполнения */
                            .time-estimate {
                                background-color: #e3f2fd;
                                padding: 15px 20px;
                                border-radius: 8px;
                                margin: 15px 0;
                                border-left: 4px solid #2196f3;
                                color: #0d47a1;
                                font-size: 20px;
                                font-weight: 600;
                                text-align: center;
                            }
                            /* Информация о задании */
                            .task-info {
                                background-color: #f8f9fa;
                                padding: 25px;
                                border-radius: 8px;
                                margin: 20px 0;
                                border: 1px solid #e0e0e0;
                            }
                            .task-name {
                                color: #1a73e8;
                                font-size: 22px;
                                margin-bottom: 20px;
                                font-weight: bold;
                            }
                            .explanation {
                                margin: 20px 0;
                                padding: 15px;
                                background-color: #ffffff;
                                border-radius: 6px;
                                border: 1px solid #e0e0e0;
                            }
                            .explanation-title {
                                color: #374151;
                                font-weight: 600;
                                margin-bottom: 10px;
                            }
                            /* Подвал */
                            .footer {
                                margin-top: 30px;
                                padding-top: 20px;
                                border-top: 1px solid #e0e0e0;
                                color: #6b7280;
                                font-size: 14px;
                            }
                            .footer p {
                                margin: 8px 0;
                            }
                            /* Адаптивность для мобильных устройств */
                            @media screen and (max-width: 600px) {
                                .container {
                                    padding: 15px;
                                    margin: 10px;
                                }
                                .task-info {
                                    padding: 15px;
                                }
                                .header {
                                    font-size: 20px;
                                }
                                .task-name {
                                    font-size: 18px;
                                }
                            }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                Оценка времени выполнения задания
                            </div>
                            <div class="task-info">
                                <div class="task-name">
                                    %s
                                </div>
                                <div class="time-estimate">
                                    ⏱️ Оценка времени выполнения: %s
                                </div>
                                <div class="explanation">
                                    <div class="explanation-title">📋 Подробное объяснение:</div>
                                    %s
                                </div>
                            </div>
                            <div class="footer">
                                <p>Это автоматическая оценка времени, основанная на анализе содержания задания.</p>
                                <p>💡 Фактическое время выполнения может отличаться в зависимости от ваших навыков и опыта.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """,
                taskName,
                formattedTime,
                formattedExplanation
            );

            helper.setText(htmlMsg, true);
            mailSender.send(mimeMessage);

        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    @Async
    public void sendSemesterTasksAnalysisNotification(String to, List<TaskTimeEstimateResponse> taskResponses, Date date) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(to);
            
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
            String dateFormatted = dateFormat.format(date);
            
            helper.setSubject("Анализ заданий по семестру: " + dateFormatted);

            int totalMinutes = taskResponses.stream()
                .filter(task -> task.getEstimatedMinutes() != null)
                .mapToInt(TaskTimeEstimateResponse::getEstimatedMinutes)
                .sum();
            
            // Форматируем общее время в часы и минуты
            String totalTime;
            if (totalMinutes >= 60) {
                int hours = totalMinutes / 60;
                int minutes = totalMinutes % 60;
                totalTime = hours + " ч. " + (minutes > 0 ? minutes + " мин." : "");
            } else {
                totalTime = totalMinutes + " мин.";
            }

            StringBuilder tasksHtml = new StringBuilder();
            
            for (TaskTimeEstimateResponse task : taskResponses) {
                String taskTime;
                if (task.getEstimatedMinutes() != null) {
                    if (task.getEstimatedMinutes() >= 60) {
                        int hours = task.getEstimatedMinutes() / 60;
                        int minutes = task.getEstimatedMinutes() % 60;
                        taskTime = hours + " ч. " + (minutes > 0 ? minutes + " мин." : "");
                    } else {
                        taskTime = task.getEstimatedMinutes() + " мин.";
                    }
                } else {
                    taskTime = "Не определено";
                }
                
                String taskExplanation = task.getExplanation() != null ?
                    task.getExplanation().replace("\n", "<br>") : 
                    "Объяснение отсутствует";
                
                tasksHtml.append(String.format("""
                    <div class="task-item">
                        <div class="task-header">
                            <div class="task-name">%s</div>
                            <div class="task-time">⏱️ %s</div>
                        </div>
                        <div class="task-explanation">
                            <div class="explanation-toggle" onclick="toggleExplanation(this)">Показать объяснение ▼</div>
                            <div class="explanation-content" style="display: none;">%s</div>
                        </div>
                    </div>
                    """, task.getTaskName(), taskTime, taskExplanation));
            }

            String htmlMsg = String.format("""
                    <!DOCTYPE html>
                    <html lang="ru">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            /* Основные стили */
                            body {
                                font-family: 'Segoe UI', Arial, sans-serif;
                                line-height: 1.6;
                                color: #333;
                                margin: 0;
                                padding: 0;
                                background-color: #f5f5f5;
                            }
                            .container {
                                max-width: 600px;
                                margin: 20px auto;
                                background-color: #ffffff;
                                padding: 30px;
                                border-radius: 10px;
                                box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                            }
                            /* Заголовок */
                            .header {
                                color: #1a73e8;
                                font-size: 24px;
                                margin-bottom: 25px;
                                padding-bottom: 15px;
                                border-bottom: 2px solid #e0e0e0;
                                font-weight: 600;
                            }
                            /* Общее время */
                            .total-time {
                                background-color: #e8f5e9;
                                padding: 15px 20px;
                                border-radius: 8px;
                                margin: 15px 0;
                                border-left: 4px solid #4caf50;
                                color: #2e7d32;
                                font-size: 20px;
                                font-weight: 600;
                                text-align: center;
                            }
                            /* Список заданий */
                            .tasks-list {
                                background-color: #f8f9fa;
                                padding: 25px;
                                border-radius: 8px;
                                margin: 20px 0;
                                border: 1px solid #e0e0e0;
                            }
                            .task-item {
                                border-bottom: 1px solid #e0e0e0;
                                padding: 15px 0;
                            }
                            .task-item:last-child {
                                border-bottom: none;
                            }
                            .task-header {
                                display: flex;
                                justify-content: space-between;
                                align-items: center;
                                margin-bottom: 10px;
                            }
                            .task-name {
                                color: #1a73e8;
                                font-size: 18px;
                                font-weight: bold;
                            }
                            .task-time {
                                color: #0d47a1;
                                font-weight: 600;
                                background-color: #e3f2fd;
                                padding: 5px 10px;
                                border-radius: 5px;
                            }
                            .task-explanation {
                                margin-top: 10px;
                            }
                            .explanation-toggle {
                                color: #1a73e8;
                                cursor: pointer;
                                user-select: none;
                                font-weight: 500;
                            }
                            .explanation-content {
                                margin-top: 10px;
                                padding: 10px;
                                background-color: #ffffff;
                                border-radius: 6px;
                                border: 1px solid #e0e0e0;
                            }
                            /* Подвал */
                            .footer {
                                margin-top: 30px;
                                padding-top: 20px;
                                border-top: 1px solid #e0e0e0;
                                color: #6b7280;
                                font-size: 14px;
                            }
                            .footer p {
                                margin: 8px 0;
                            }
                            /* Адаптивность для мобильных устройств */
                            @media screen and (max-width: 600px) {
                                .container {
                                    padding: 15px;
                                    margin: 10px;
                                }
                                .task-header {
                                    flex-direction: column;
                                    align-items: flex-start;
                                }
                                .task-time {
                                    margin-top: 5px;
                                }
                            }
                        </style>
                        <script>
                            function toggleExplanation(element) {
                                var content = element.nextElementSibling;
                                if (content.style.display === "none") {
                                    content.style.display = "block";
                                    element.innerHTML = "Скрыть объяснение ▲";
                                } else {
                                    content.style.display = "none";
                                    element.innerHTML = "Показать объяснение ▼";
                                }
                            }
                        </script>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                Анализ заданий по семестру: %s
                            </div>
                            <div class="total-time">
                                ⏳ Общее время на выполнение всех заданий: %s
                            </div>
                            <div class="tasks-list">
                                <h3>Задания в семестре (%d):</h3>
                                %s
                            </div>
                            <div class="footer">
                                <p>Это автоматическая оценка времени, основанная на анализе содержания заданий.</p>
                                <p>💡 Фактическое время выполнения может отличаться в зависимости от ваших навыков и опыта.</p>
                                <p>📊 Рекомендуется планировать учебное время с учетом этих оценок.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """,
                dateFormatted,
                totalTime,
                taskResponses.size(),
                tasksHtml.toString()
            );

            helper.setText(htmlMsg, true);
            mailSender.send(mimeMessage);

        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    @Async
    public void sendStudyPlanNotification(String to, StudyPlanResponse studyPlan, LocalDate semesterDate) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(to);
            
            String semesterName;
            int month = semesterDate.getMonthValue();
            if (month >= 9) {
                semesterName = "Осенний семестр " + semesterDate.getYear();
            } else if (month <= 6) {
                semesterName = "Весенний семестр " + semesterDate.getYear();
            } else {
                semesterName = "Семестр от " + semesterDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            }
            
            helper.setSubject("Учебный план: " + semesterName);

            StringBuilder daysHtml = new StringBuilder();
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("ru"));
            
            for (StudyPlanResponse.DailyPlan dailyPlan : studyPlan.getDailyPlans()) {
                String dayName = dailyPlan.getDate().format(dateFormatter);
                
                String totalTime;
                if (dailyPlan.getTotalMinutes() >= 60) {
                    int hours = dailyPlan.getTotalMinutes() / 60;
                    int minutes = dailyPlan.getTotalMinutes() % 60;
                    totalTime = hours + " ч. " + (minutes > 0 ? minutes + " мин." : "");
                } else {
                    totalTime = dailyPlan.getTotalMinutes() + " мин.";
                }
                
                daysHtml.append(String.format("""
                    <div class="day-item">
                        <div class="day-header">
                            <div class="day-name">%s</div>
                            <div class="day-time">Общее время: %s</div>
                        </div>
                        <div class="tasks-container">
                    """, dayName, totalTime));
                
                if (dailyPlan.getTasks() != null && !dailyPlan.getTasks().isEmpty()) {
                    for (StudyPlanResponse.TaskPlan task : dailyPlan.getTasks()) {
                        String taskTime;
                        if (task.getDurationMinutes() >= 60) {
                            int hours = task.getDurationMinutes() / 60;
                            int minutes = task.getDurationMinutes() % 60;
                            taskTime = hours + " ч. " + (minutes > 0 ? minutes + " мин." : "");
                        } else {
                            taskTime = task.getDurationMinutes() + " мин.";
                        }
                        
                        String deadlineStr = "Нет";
                        if (task.getDeadline() != null) {
                            SimpleDateFormat deadlineFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
                            deadlineStr = deadlineFormat.format(task.getDeadline());
                        }
                        
                        String displayTaskName = task.getTaskName();
                        if (displayTaskName != null && displayTaskName.trim().endsWith("Задание")) {
                            displayTaskName = displayTaskName.substring(0, displayTaskName.length() - 7).trim();
                        }
                        
                        daysHtml.append(String.format("""
                            <div class="task-item">
                                <div class="task-header">
                                    <div class="task-name">%s</div>
                                    <div class="task-time">⏱️ %s</div>
                                </div>
                                <div class="task-details">
                                    <div class="subject">📚 Предмет: %s</div>
                                    <div class="deadline">📅 Дедлайн: %s</div>
                                </div>
                            </div>
                        """, displayTaskName, taskTime, task.getSubjectName(), deadlineStr));
                    }
                } else {
                    daysHtml.append("<div class=\"no-tasks\">Нет заданий на этот день</div>");
                }
                
                daysHtml.append("""
                        </div>
                    </div>
                """);
            }
            
            String warningsHtml = "";
            if (studyPlan.getWarnings() != null && !studyPlan.getWarnings().isEmpty()) {
                StringBuilder warningsList = new StringBuilder();
                for (String warning : studyPlan.getWarnings()) {
                    warningsList.append(String.format("<li>%s</li>", warning));
                }
                
                warningsHtml = String.format("""
                    <div class="warnings-section">
                        <h3>⚠️ Важные предупреждения:</h3>
                        <ul class="warnings-list">
                            %s
                        </ul>
                    </div>
                """, warningsList.toString());
            }
            
            String statsHtml = String.format("""
                <div class="stats-section">
                    <div class="stat-item">
                        <div class="stat-label">Всего заданий:</div>
                        <div class="stat-value">%d</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-label">Распланировано заданий:</div>
                        <div class="stat-value">%d</div>
                    </div>
                    <div class="stat-item">
                        <div class="stat-label">Процент покрытия:</div>
                        <div class="stat-value">%d%%</div>
                    </div>
                </div>
            """, studyPlan.getTotalTasks(), studyPlan.getPlannedTasks(), 
                studyPlan.getTotalTasks() > 0 ? (studyPlan.getPlannedTasks() * 100 / studyPlan.getTotalTasks()) : 0);

            String htmlMsg = String.format("""
                    <!DOCTYPE html>
                    <html lang="ru">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            /* Основные стили */
                            body {
                                font-family: 'Segoe UI', Arial, sans-serif;
                                line-height: 1.6;
                                color: #333;
                                margin: 0;
                                padding: 0;
                                background-color: #f5f5f5;
                            }
                            .container {
                                max-width: 800px;
                                margin: 20px auto;
                                background-color: #ffffff;
                                padding: 30px;
                                border-radius: 10px;
                                box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                            }
                            /* Заголовок */
                            .header {
                                color: #1a73e8;
                                font-size: 24px;
                                margin-bottom: 25px;
                                padding-bottom: 15px;
                                border-bottom: 2px solid #e0e0e0;
                                font-weight: 600;
                                text-align: center;
                            }
                            /* Секция с предупреждениями */
                            .warnings-section {
                                background-color: #fff8e1;
                                padding: 15px 20px;
                                border-radius: 8px;
                                margin: 20px 0;
                                border-left: 4px solid #ffc107;
                            }
                            .warnings-section h3 {
                                color: #e65100;
                                margin-top: 0;
                            }
                            .warnings-list {
                                margin: 10px 0;
                                padding-left: 20px;
                            }
                            .warnings-list li {
                                margin-bottom: 8px;
                                color: #e65100;
                            }
                            /* Статистика */
                            .stats-section {
                                display: flex;
                                justify-content: space-between;
                                background-color: #e8f5e9;
                                padding: 15px 20px;
                                border-radius: 8px;
                                margin: 20px 0;
                                border-left: 4px solid #4caf50;
                            }
                            .stat-item {
                                text-align: center;
                                flex: 1;
                            }
                            .stat-label {
                                color: #2e7d32;
                                font-weight: 500;
                                margin-bottom: 5px;
                            }
                            .stat-value {
                                color: #1b5e20;
                                font-size: 20px;
                                font-weight: 600;
                            }
                            /* Список дней */
                            .days-list {
                                margin: 30px 0;
                            }
                            .day-item {
                                background-color: #f8f9fa;
                                padding: 20px;
                                border-radius: 8px;
                                margin-bottom: 20px;
                                border: 1px solid #e0e0e0;
                            }
                            .day-header {
                                display: flex;
                                justify-content: space-between;
                                align-items: center;
                                margin-bottom: 15px;
                                padding-bottom: 10px;
                                border-bottom: 1px solid #e0e0e0;
                            }
                            .day-name {
                                color: #1a73e8;
                                font-size: 18px;
                                font-weight: bold;
                            }
                            .day-time {
                                color: #0d47a1;
                                font-weight: 600;
                                background-color: #e3f2fd;
                                padding: 5px 10px;
                                border-radius: 5px;
                            }
                            /* Задания */
                            .tasks-container {
                                display: grid;
                                grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
                                gap: 15px;
                            }
                            .task-item {
                                background-color: #ffffff;
                                padding: 15px;
                                border-radius: 6px;
                                border: 1px solid #e0e0e0;
                                transition: all 0.3s ease;
                            }
                            .task-item:hover {
                                box-shadow: 0 5px 15px rgba(0,0,0,0.1);
                                transform: translateY(-2px);
                            }
                            .task-header {
                                margin-bottom: 10px;
                            }
                            .task-name {
                                color: #1a73e8;
                                font-weight: bold;
                                margin-bottom: 5px;
                            }
                            .task-time {
                                display: inline-block;
                                background-color: #e3f2fd;
                                color: #0d47a1;
                                padding: 3px 8px;
                                border-radius: 4px;
                                font-size: 14px;
                                font-weight: 500;
                            }
                            .task-details {
                                color: #5f6368;
                                font-size: 14px;
                            }
                            .subject, .deadline {
                                margin-bottom: 5px;
                            }
                            .no-tasks {
                                color: #757575;
                                font-style: italic;
                                text-align: center;
                                padding: 20px;
                            }
                            /* Подвал */
                            .footer {
                                margin-top: 30px;
                                padding-top: 20px;
                                border-top: 1px solid #e0e0e0;
                                color: #6b7280;
                                font-size: 14px;
                                text-align: center;
                            }
                            .footer p {
                                margin: 8px 0;
                            }
                            /* Адаптивность для мобильных устройств */
                            @media screen and (max-width: 600px) {
                                .container {
                                    padding: 15px;
                                    margin: 10px;
                                }
                                .stats-section {
                                    flex-direction: column;
                                }
                                .stat-item {
                                    margin-bottom: 10px;
                                }
                                .tasks-container {
                                    grid-template-columns: 1fr;
                                }
                                .day-header {
                                    flex-direction: column;
                                    align-items: flex-start;
                                }
                                .day-time {
                                    margin-top: 5px;
                                }
                            }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <div class="header">
                                Учебный план: %s
                            </div>
                            %s
                            %s
                            <div class="days-list">
                                %s
                            </div>
                            <div class="footer">
                                <p>Это автоматически сгенерированный учебный план.</p>
                                <p>💡 План составлен с учетом оптимального распределения нагрузки.</p>
                                <p>📊 Рекомендуется придерживаться плана для эффективного обучения.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """,
                semesterName,
                warningsHtml,
                statsHtml,
                daysHtml.toString()
            );

            helper.setText(htmlMsg, true);
            mailSender.send(mimeMessage);

        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}