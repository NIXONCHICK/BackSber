package back.services;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.text.SimpleDateFormat;
import java.util.Date;

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

      String htmlMsg = String.format("""
              <html>
              <head>
                  <style>
                      body { font-family: Arial, sans-serif; }
                      .container { padding: 20px; }
                      .header { color: #2c3e50; font-size: 24px; margin-bottom: 20px; }
                      .task-info { background-color: #f8f9fa; padding: 15px; border-radius: 5px; }
                      .task-name { color: #2980b9; font-size: 20px; margin-bottom: 10px; }
                      .deadline { color: #e74c3c; margin: 10px 0; }
                      .description { margin: 15px 0; }
                      .footer { margin-top: 20px; color: #7f8c8d; font-size: 14px; }
                      .elder-note { background-color: #e8f5e9; padding: 10px; border-radius: 5px; margin: 10px 0; }
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
                              <strong>Срок сдачи:</strong> %s
                          </div>
                          <div class="description">
                              <strong>Описание задания:</strong><br>
                              %s
                          </div>
                      </div>
                      <div class="footer">
                          <p>%s</p>
                          <p>Для просмотра деталей задания войдите в систему.</p>
                      </div>
                  </div>
              </body>
              </html>
              """,
          isElder ? "Подтверждение создания задания для группы " + groupName : "Новое задание для группы " + groupName,
          isElder ? "<div class='elder-note'>Вы успешно создали новое задание для своей группы.</div>" : "",
          taskName,
          deadlineFormatted,
          description != null ? description : "Описание отсутствует",
          isElder ? "Это подтверждение создания задания. Студенты группы получат уведомления." : "Это автоматическое уведомление. Пожалуйста, не отвечайте на него."
      );

      helper.setText(htmlMsg, true);
      mailSender.send(mimeMessage);

    } catch (MessagingException e) {
      e.printStackTrace();
    }
  }
} 