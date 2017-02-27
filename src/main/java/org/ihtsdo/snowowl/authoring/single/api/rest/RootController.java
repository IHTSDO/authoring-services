package org.ihtsdo.snowowl.authoring.single.api.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
public class RootController {

	@RequestMapping("/")
	public void getRoot(HttpServletResponse response) throws IOException {
		response.sendRedirect("swagger-ui.html");
	}

}
