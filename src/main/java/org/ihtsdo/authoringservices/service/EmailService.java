package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

@Service
public class EmailService {

    public static final String RECEIVER_NAMES_STRING = "receiverNames";
    public static final String TASK_ID_STRING = "taskId";
    public static final String SUMMARY_STRING = "summary";
    public static final String STATUS_STRING = "status";
    public static final String DATE_STRING = "date";
    public static final String TASK_TITLE_STRING = "taskTitle";
    public static final String COMMENT_STRING = "comment";

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
        StringBuilder receiverNames = new StringBuilder();
        Collection <String> emails = new ArrayList <>();
        for (User user : recipients) {
            if (isAllowedEmailNotification(user.getUsername())) {
                receiverNames.append(!receiverNames.isEmpty() ? ", " + user.getDisplayName() : user.getDisplayName());
                emails.add(user.getEmail());
            }
        }

        Context params = new Context();
        params.setVariable(RECEIVER_NAMES_STRING, receiverNames.toString());
        params.setVariable(TASK_ID_STRING, taskKey);
        params.setVariable(TASK_TITLE_STRING, taskTitle);
        params.setVariable(COMMENT_STRING, comment);
        params.setVariable(DATE_STRING, SIMPLE_DATE_FORMAT.format(new Date()));
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
        params.setVariable(RECEIVER_NAMES_STRING, receiverNames.toString());
        params.setVariable(TASK_ID_STRING, rmpTaskId);
        params.setVariable(SUMMARY_STRING, summary);
        params.setVariable(STATUS_STRING, status);
        params.setVariable(DATE_STRING, SIMPLE_DATE_FORMAT.format(new Date()));

        String subject = "Status change";
        String templateFile = "Notify-Status-Template-RMP";
        doSendMail(subject, templateFile, params, emails);
    }

    public void sendRMPTaskCreatedNotification(long rmpTaskId, String summary, Collection<User> recipients) {
        StringBuilder receiverNames = new StringBuilder();
        Collection<String> emails = new ArrayList<>();
        for (User user : recipients) {
            receiverNames.append(!receiverNames.isEmpty() ? ", " + user.getDisplayName() : user.getDisplayName());
            emails.add(user.getEmail());
        }
        Context params = new Context();
        params.setVariable(RECEIVER_NAMES_STRING, receiverNames.toString());
        params.setVariable(TASK_ID_STRING, rmpTaskId);
        params.setVariable(SUMMARY_STRING, summary);
        params.setVariable(DATE_STRING, SIMPLE_DATE_FORMAT.format(new Date()));

        String subject = "New RMP request created";
        String templateFile = "Notify-Creation-Template-RMP";
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
        params.setVariable(RECEIVER_NAMES_STRING, receiverNames.toString());
        params.setVariable(TASK_ID_STRING, rmpTaskId);
        params.setVariable(SUMMARY_STRING, summary);
        params.setVariable(COMMENT_STRING, comment);
        params.setVariable(DATE_STRING, SIMPLE_DATE_FORMAT.format(new Date()));

        String subject = "Comment added";
        String templateFile = "Notify-Comment-Template-RMP";
        doSendMail(subject, templateFile, params, emails);
    }

    private void sendTaskStatusChangeNotification(String projectKey, String taskKey, String taskTitle, Collection <User> recipients, String subject, String templateFile, String view) {
        StringBuilder receiverNames = new StringBuilder();
        Collection <String> emails = new ArrayList <>();
        for (User user : recipients) {
            if (isAllowedEmailNotification(user.getUsername())) {
                receiverNames.append((!receiverNames.isEmpty()) ? ", " + user.getDisplayName() : user.getDisplayName());
                emails.add(user.getEmail());
            }
        }
        Context params = new Context();
        params.setVariable(RECEIVER_NAMES_STRING, receiverNames.toString());
        params.setVariable(TASK_ID_STRING, taskKey);
        params.setVariable(TASK_TITLE_STRING, taskTitle);
        params.setVariable(DATE_STRING, SIMPLE_DATE_FORMAT.format(new Date()));
        String url = rootURL + "/#/tasks/task/" + projectKey + "/" + taskKey + "/" + view;
        params.setVariable("requestUrl", url);
        doSendMail(subject, templateFile, params, emails);
    }

	private boolean isAllowedEmailNotification(String username) {
		try {
			JsonNode userPreferences = uiStateService.retrievePanelStateWithoutThrowingResourceNotFoundException(
					username,
					"user-preferences"
			);

			if (userPreferences != null) {
				JsonNode allowed = userPreferences.get("allowedEmailNotification");
				if (allowed != null && allowed.isBoolean()) {
					return allowed.asBoolean();
				}
			}
		} catch (IOException e) {
			logger.info("Could not retrieve user-preferences for user: {}", username, e);
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
            String emailContent = this.templateEngine.process(templateFile, params);
            message.setText(emailContent, "utf-8", "html");
            logger.info("Sending email to {}} with subject {}", toEmails, subject);
            send(message);
        } catch (UnsupportedEncodingException e) {
            logger.error("The character encoding is not supported. {}", e.getMessage());
        } catch (MessagingException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public void send(MimeMessage mimeMessage) {
        try {
            mailSender.send(mimeMessage);
        } catch (MailException e) {
            logger.debug("Error sending email notification: {}", e.getMessage());
        }
    }
}
