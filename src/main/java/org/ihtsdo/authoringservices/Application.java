package org.ihtsdo.authoringservices;

import org.ihtsdo.authoringservices.configuration.Configuration;
import org.springframework.boot.SpringApplication;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.scheduling.annotation.EnableScheduling;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableSwagger2
@EnableScheduling
@EnableJms
public class Application extends Configuration {
	
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
