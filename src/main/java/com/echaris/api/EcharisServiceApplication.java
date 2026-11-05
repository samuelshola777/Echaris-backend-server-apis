package com.echaris.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
@EnableAsync
@EnableTransactionManagement
@EnableScheduling
    @OpenAPIDefinition
public class EcharisServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(EcharisServiceApplication.class, args);
	}

}
