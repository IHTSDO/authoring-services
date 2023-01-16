package org.ihtsdo.authoringservices.service.monitor;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.authoringservices.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class MonitorService {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MonitorFactory monitorFactory;

    private final Map<String, UserMonitors> userMonitorsMap = new HashMap<>();

    private final Logger logger = LoggerFactory.getLogger(getClass());

	public void updateUserFocus(String username, String token, String focusProjectId, String focusTaskId) throws BusinessServiceException {
		logger.info("Starting: Updating user focus for {} [{}/{}]", username, focusProjectId, focusTaskId);
		createIfNotExists(username, token);
		userMonitorsMap.get(username).updateFocus(focusProjectId, focusTaskId);
		logger.info("Finished: Updating user focus for {} [{}/{}]", username, focusProjectId, focusTaskId);
	}

	public void keepMonitorsAlive(String username) {
		final UserMonitors userMonitors = userMonitorsMap.get(username);
		if (userMonitors != null) {
			userMonitors.accessed();
		}
	}

	private void createIfNotExists(final String username, String token) {
		if (!userMonitorsMap.containsKey(username)) {
			synchronized(MonitorService.class) {
				if (!userMonitorsMap.containsKey(username)) {
					final Runnable deathCallback = () -> {
                        synchronized (MonitorService.class) {
                            userMonitorsMap.remove(username);
                        }
                    };
					final UserMonitors userMonitors = new UserMonitors(username, monitorFactory, notificationService, deathCallback);
					userMonitors.setToken(token);
					this.userMonitorsMap.put(username, userMonitors);
				}
			}
		} else {
			final UserMonitors userMonitors = this.userMonitorsMap.get(username);
			userMonitors.setToken(token);
		}
	}

}
