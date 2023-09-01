package org.ihtsdo.authoringservices;

import org.ihtsdo.authoringservices.configuration.Configuration;
import org.springframework.boot.SpringApplication;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableJms
@EnableAsync
public class Application extends Configuration {
	
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
