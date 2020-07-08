package org.ihtsdo.snowowl.authoring.single.api.service;

import org.ihtsdo.snowowl.authoring.single.api.pojo.User;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring4.SpringTemplateEngine;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.Future;

@Service
public class EmailService {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Value("${email.user.from}")
	private String from;

	@Value("${root.url}")
	private String rootURL;

	@Autowired
    private JavaMailSender mailSender;

    @Autowired
    private SpringTemplateEngine templateEngine;

	@Autowired
	private UiStateService uiStateService;

	public void sendTaskReviewAssingedNotification(String projectKey, String taskKey, String taskTitle, Collection<User> recipients) {
		if (null == recipients || 0 == recipients.size()) {
			return;
		}
        String subject = "SNOMED International - Task review assignment";
        String templateFile = "Notify-Task-Review-Assign-Template";
		String view = "feedback";
		sendTaskStatusChangeNotification(projectKey, taskKey, taskTitle, recipients, subject, templateFile, view);
	}

	public void sendTaskReviewCompletedNotification(String projectKey, String taskKey, String taskTitle, Collection<User> recipients) {
		if (null == recipients || 0 == recipients.size()) {
			return;
		}
		String subject = "SNOMED International - Task review completed";
		String templateFile = "Notify-Task-Review-Complete-Template";
		String view = "edit";
		sendTaskStatusChangeNotification(projectKey, taskKey, taskTitle, recipients, subject, templateFile, view);
	}

	public void sendCommentAddedNotification(String projectKey, String taskKey, String taskTitle, String comment, Collection<User> recipients) {
		if (null == recipients || 0 == recipients.size()) {
			return;
		}
		String subject = "SNOMED International - Comment added";
		String templateFile = "Notify-Comment-Template";

		String receiverNames = "";
		Collection<String> emails = new ArrayList<>();
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
		params.setVariable("date", formatDate(new Date()));
		String url = rootURL + "/#/tasks/task/" + projectKey + "/" + taskKey + "/feedback";
		params.setVariable("requestUrl", url);
		try {
			doSendMail(subject, templateFile, params, emails);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
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
		params.setVariable("date", formatDate(new Date()));
		String url = rootURL + "/#/tasks/task/" + projectKey + "/" + taskKey + "/" + view;
		params.setVariable("requestUrl", url);
		try {
			doSendMail(subject, templateFile, params, emails);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	private boolean isAllowedEmailNotification(String username) {
		try {
			String userPreference = uiStateService.retrievePanelState(username, "user-preferences");
			if (userPreference != null) {
				JSONObject jsonObject = new JSONObject(userPreference);
				return jsonObject.getBoolean("allowedEmailNotification");
			}
			else {
				return false;
			}
		} catch (IOException e) {
			logger.info("Could not find user-preferences for user: " + username);
			return  false;
		} catch (JSONException e) {
			logger.info("The JSON property allowedEmailNotification does not present in user-preferences for user: " + username);
			return  false;
		}
	}

	private String formatDate(Date date) {
		DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		return df.format(date);
	}

	private Future<MimeMessage> doSendMail(final String subject, final String templateFile, final Context params,
										   final Collection<String> toEmails) {
		if (null == toEmails || 0 == toEmails.size()) {
			return null;
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
			return send(message);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}

	@Async
	private Future<MimeMessage> send(MimeMessage mimeMessage) {

		try {
			mailSender.send(mimeMessage);
		} catch (Exception e) {
			logger.debug("Error sending notification: " + e.getMessage());
			mimeMessage = null;
		}

		return new AsyncResult<>(mimeMessage);
	}
}
