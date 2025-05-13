package org.ihtsdo.authoringservices.service;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.ihtsdo.authoringservices.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

@Service
public class EmailService {

    @Value("${email.user.from}")
    private String from;

    @Value("${email.link.platform.url}")
    private String rootURL;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private UiStateService uiStateService;

    private static final DateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public void sendTaskReviewAssignedNotification(String projectKey, String taskKey, String taskTitle, Collection <User> recipients) {
        if (CollectionUtils.isEmpty(recipients)) {
            return;
        }
        String subject = "SNOMED International - Task review assignment";
        String templateFile = "Notify-Task-Review-Assign-Template";
        String view = "feedback";
        sendTaskStatusChangeNotification(projectKey, taskKey, taskTitle, recipients, subject, templateFile, view);
    }

    public void sendTaskReviewCompletedNotification(String projectKey, String taskKey, String taskTitle, Collection <User> recipients) {
        if (CollectionUtils.isEmpty(recipients)) {
            return;
        }
        String subject = "SNOMED International - Task review completed";
        String templateFile = "Notify-Task-Review-Complete-Template";
        String view = "edit";
        sendTaskStatusChangeNotification(projectKey, taskKey, taskTitle, recipients, subject, templateFile, view);
    }

    public void sendCommentAddedNotification(String projectKey, String taskKey, String taskTitle, String comment, Collection <User> recipients) {
        if (CollectionUtils.isEmpty(recipients)) {
            return;
        }
        String subject = "SNOMED International - Comment added";
        String templateFile = "Notify-Comment-Template";
        String receiverNames = "";
        Collection <String> emails = new ArrayList <>();
        for (User user : recipients) {
            if (isAllowedEmailNotification(user.getUsername())) {
                receiverNames += receiverNames.length() > 0 ? ", " + user.getDisplayName() : user.getDisplayName();
                emails.add(user.getEmail());
            }
        }

        Context params = new Context();
        params.setVariable("receiverNames", receiverNames);
        params.setVariable("taskId", taskKey);
        params.setVariable("taskTitle", taskTitle);
        params.setVariable("comment", comment);
        params.setVariable("date", SIMPLE_DATE_FORMAT.format(new Date()));
        String url = rootURL + "/#/tasks/task/" + projectKey + "/" + taskKey + "/feedback";
        params.setVariable("requestUrl", url);
        doSendMail(subject, templateFile, params, emails);
    }

    public void sendRMPTaskStatusChangeNotification(long rmpTaskId, String summary, String status, Collection<User> recipients) {
        StringBuilder receiverNames = new StringBuilder();
        Collection <String> emails = new ArrayList <>();
        for (User user : recipients) {
            receiverNames.append(!receiverNames.isEmpty() ? ", " + user.getDisplayName() : user.getDisplayName());
            emails.add(user.getEmail());
        }
        Context params = new Context();
        params.setVariable("receiverNames", receiverNames.toString());
        params.setVariable("taskId", rmpTaskId);
        params.setVariable("summary", summary);
        params.setVariable("status", status);
        params.setVariable("date", SIMPLE_DATE_FORMAT.format(new Date()));

        String subject = "Status change";
        String templateFile = "Notify-Status-Template-RMP";
        doSendMail(subject, templateFile, params, emails);
    }

    public void sendRMPTaskCommentAddNotification(long rmpTaskId, String summary, String comment, Collection<User> recipients) {
        StringBuilder receiverNames = new StringBuilder();
        Collection <String> emails = new ArrayList <>();
        for (User user : recipients) {
            receiverNames.append(!receiverNames.isEmpty() ? ", " + user.getDisplayName() : user.getDisplayName());
            emails.add(user.getEmail());
        }
        Context params = new Context();
        params.setVariable("receiverNames", receiverNames.toString());
        params.setVariable("taskId", rmpTaskId);
        params.setVariable("summary", summary);
        params.setVariable("comment", comment);
        params.setVariable("date", SIMPLE_DATE_FORMAT.format(new Date()));

        String subject = "Comment added";
        String templateFile = "Notify-Comment-Template-RMP";
        doSendMail(subject, templateFile, params, emails);
    }

    private void sendTaskStatusChangeNotification(String projectKey, String taskKey, String taskTitle, Collection <User> recipients, String subject, String templateFile, String view) {
        String receiverNames = "";
        Collection <String> emails = new ArrayList <>();
        for (User user : recipients) {
            if (isAllowedEmailNotification(user.getUsername())) {
                receiverNames += receiverNames.length() > 0 ? ", " + user.getDisplayName() : user.getDisplayName();
                emails.add(user.getEmail());
            }
        }
        Context params = new Context();
        params.setVariable("receiverNames", receiverNames);
        params.setVariable("taskId", taskKey);
        params.setVariable("taskTitle", taskTitle);
        params.setVariable("date", SIMPLE_DATE_FORMAT.format(new Date()));
        String url = rootURL + "/#/tasks/task/" + projectKey + "/" + taskKey + "/" + view;
        params.setVariable("requestUrl", url);
        doSendMail(subject, templateFile, params, emails);
    }

    private boolean isAllowedEmailNotification(String username) {
        try {
            String userPreference = uiStateService.retrievePanelStateWithoutThrowingResourceNotFoundException(username, "user-preferences");
            if (userPreference != null) {
                JSONObject jsonObject = new JSONObject(userPreference);
                return jsonObject.getBoolean("allowedEmailNotification");
            }
        } catch (IOException e) {
            logger.info("Could not find user-preferences for user: " + username);
        } catch (JSONException e) {
            logger.info("The JSON property allowedEmailNotification does not present in user-preferences for user: " + username);

        }
        return false;
    }

    private void doSendMail(final String subject, final String templateFile, final Context params, final Collection <String> toEmails) {
        if (CollectionUtils.isEmpty(toEmails)) {
            return;
        }
        try {
            Session session = Session.getDefaultInstance(System.getProperties());
            // Create a default MimeMessage object.
            MimeMessage message = new MimeMessage(session);
            // To get the array of addresses
            for (String email : toEmails) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(email));
            }
            message.setFrom(new InternetAddress(from, from));
            message.setSubject(subject);
            String emailContent = EmailService.this.templateEngine.process(templateFile, params);
            message.setText(emailContent, "utf-8", "html");
            logger.info(String.format("Sending email to %s with subject %s", toEmails, subject));
            send(message);
        } catch (UnsupportedEncodingException e) {
            logger.error("The character encoding is not supported. " + e.getMessage());
        } catch (MessagingException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Async
    private void send(MimeMessage mimeMessage) {
        try {
            mailSender.send(mimeMessage);
        } catch (MailException e) {
            logger.debug("Error sending email notification: " + e.getMessage());
        }
    }
}
