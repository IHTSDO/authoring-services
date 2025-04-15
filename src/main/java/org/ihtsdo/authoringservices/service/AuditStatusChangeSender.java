package org.ihtsdo.authoringservices.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditStatusChangeSender {

    private final JmsTemplate jmsTemplate;

    @Value("${audit.jms.queue.prefix}")
    private String jmsQueuePrefix;

    @Autowired
    public AuditStatusChangeSender(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public static final String QUEUE_SUFFIX = ".audit";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void sendMessage(final String taskKey, final String branchPath, final String username, final String statusFrom, final String statusTo, Long timestamp) {
        String queueName = jmsQueuePrefix + QUEUE_SUFFIX;
        logger.info("RequestStatusChangeSender - Send message to queue {} ", queueName);
        Message mapMessage = new ActiveMQTextMessage();
        try {
            mapMessage.setStringProperty("recordId", String.valueOf(taskKey));
            mapMessage.setStringProperty("userId", username);
            mapMessage.setStringProperty("location", branchPath);
            mapMessage.setStringProperty("sourceApplication", "AUTHORING");
            mapMessage.setStringProperty("eventType", "STATUS_CHANGE");
            mapMessage.setStringProperty("propertyBeforeChange", statusFrom);
            mapMessage.setStringProperty("propertyAfterChange", statusTo);
            mapMessage.setStringProperty("timestamp", String.valueOf(timestamp));
            jmsTemplate.convertAndSend(queueName, mapMessage);
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }
}
