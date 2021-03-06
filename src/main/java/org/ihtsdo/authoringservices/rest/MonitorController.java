package org.ihtsdo.authoringservices.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.authoringservices.domain.Notification;
import org.ihtsdo.authoringservices.domain.UserFocusRequest;
import org.ihtsdo.authoringservices.service.NotificationService;
import org.ihtsdo.authoringservices.service.monitor.MonitorService;
import org.ihtsdo.authoringservices.service.monitor.UserMonitors;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@Api("Monitor")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class MonitorController {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private MonitorService monitorService;
	

	@ApiOperation(value="Set user focus for notifications.", notes = "A Task or Project can be monitored for " +
			"rebase opportunities or stale reports. Notifications will be made available. " +
			"Each additional POST will replace the previous monitor. " +
			"A monitor will expire after " + UserMonitors.KEEP_ALIVE_MINUTES + " minutes if the notifications endpoint is not visited by the user.")
	@RequestMapping(value="/monitor", method= RequestMethod.POST)
	public void monitor(@RequestBody UserFocusRequest userFocusRequest) throws BusinessServiceException {
		monitorService.updateUserFocus(SecurityUtil.getUsername(), SecurityUtil.getAuthenticationToken(), userFocusRequest.getProjectId(), userFocusRequest.getTaskId());
	}

}
