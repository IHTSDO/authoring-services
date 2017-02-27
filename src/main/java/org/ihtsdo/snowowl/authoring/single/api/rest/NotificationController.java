package org.ihtsdo.snowowl.authoring.single.api.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.ihtsdo.snowowl.authoring.single.api.pojo.UserFocusRequest;
import org.ihtsdo.snowowl.authoring.single.api.service.NotificationService;
import org.ihtsdo.snowowl.authoring.single.api.service.monitor.MonitorService;
import org.ihtsdo.snowowl.authoring.single.api.service.monitor.UserMonitors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@Api("Notifications")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class NotificationController {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private MonitorService monitorService;
	
	@ApiOperation(value="Retrieve new notifications", notes="Retrieve one-time user notifications; once retrieved they are lost.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/notifications", method= RequestMethod.GET)
	public List<Notification> retrieveNotifications() throws IOException {
		final String username = ControllerHelper.getUsername();
		monitorService.keepMonitorsAlive(username);
		return notificationService.retrieveNewNotifications(username);
	}

	@ApiOperation(value="Set user focus for notifications.", notes = "A Task or Project can be monitored for " +
			"rebase opportunities or stale reports. Notifications will be made available. " +
			"Each additional POST will replace the previous monitor. " +
			"A monitor will expire after " + UserMonitors.KEEP_ALIVE_MINUTES + " minutes if the notifications endpoint is not visited by the user.")
	@RequestMapping(value="/monitor", method= RequestMethod.POST)
	public void monitor(@RequestBody UserFocusRequest userFocusRequest) throws BusinessServiceException {
		monitorService.updateUserFocus(ControllerHelper.getUsername(), userFocusRequest.getProjectId(), userFocusRequest.getTaskId());
	}

}
