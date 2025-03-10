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

            // Преобразуем переносы строк в HTML
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
}