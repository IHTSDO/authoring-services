package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SnowstormBranchChangeListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String BRANCH = "branch";
    private static final String SOURCE_BRANCH = "sourceBranch";

    private final CacheService cacheService;

    private final ObjectMapper objectMapper;

    @Autowired
    public SnowstormBranchChangeListener(CacheService cacheService, ObjectMapper objectMapper) {
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    @JmsListener(destination = "${snowstorm.jms.queue.prefix}.branch.change")
    public void receiveBranchChangeMessage(String message) {
        try {
            Map<String, String> jsonObject = objectMapper.readValue(message, HashMap.class);
            if (jsonObject.containsKey(BRANCH)) {
                cacheService.clearBranchCache(jsonObject.get(BRANCH));
            }
            if (jsonObject.containsKey(SOURCE_BRANCH)) {
                cacheService.clearBranchCache(jsonObject.get(SOURCE_BRANCH));
            }
        } catch (Exception e) {
            logger.error("Error while processing message for branch changes. Message {}", e.getMessage(), e);
        }
    }

    @JmsListener(destination = "${snowstorm.jms.queue.prefix}.role.change")
    public void receiveRoleChangeMessage(String message) {
        try {
            Map<String, String> jsonObject = objectMapper.readValue(message, HashMap.class);
            if (jsonObject.containsKey(BRANCH)) {
                cacheService.clearBranchCacheStartWith(jsonObject.get(BRANCH));
            }
        } catch (Exception e) {
            logger.error("Error while processing message for role changes. Message {}", e.getMessage(), e);
        }
    }
}
