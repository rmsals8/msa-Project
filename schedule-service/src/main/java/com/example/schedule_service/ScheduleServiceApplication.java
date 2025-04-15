package com.example.schedule_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;

import io.github.cdimascio.dotenv.Dotenv;

@Import(com.example.common.config.RestTemplateConfig.class)
@SpringBootApplication
@EnableRetry
public class ScheduleServiceApplication {

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.load();
		dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
		SpringApplication.run(ScheduleServiceApplication.class, args);
	}

}
