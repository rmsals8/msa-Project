package com.example.auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
@Import(com.example.common.config.RestTemplateConfig.class)
public class AuthServiceApplication {

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.load();
		dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
