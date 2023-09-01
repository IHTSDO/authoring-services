package org.ihtsdo.authoringservices.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
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

import static org.ihtsdo.authoringservices.rest.ControllerHelper.requiredParam;

@Tag(name = "Monitor")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class MonitorController {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private MonitorService monitorService;
	

	@Operation(summary = "Set user focus for notifications",
			description = "A Task or Project can be monitored for " +
			"rebase opportunities or stale reports. Notifications will be made available. " +
			"Each additional POST will replace the previous monitor. " +
			"A monitor will expire after " + UserMonitors.KEEP_ALIVE_MINUTES + " minutes if the notifications endpoint is not visited by the user.")
	@RequestMapping(value="/monitor", method= RequestMethod.POST)
	public void monitor(@RequestBody UserFocusRequest userFocusRequest) throws BusinessServiceException {
		monitorService.updateUserFocus(SecurityUtil.getUsername(), SecurityUtil.getAuthenticationToken(),
				requiredParam(userFocusRequest.getProjectId(), "projectId"), userFocusRequest.getTaskId());
	}

}
