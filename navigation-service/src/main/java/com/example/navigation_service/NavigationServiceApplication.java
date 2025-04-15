package com.example.navigation_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import io.github.cdimascio.dotenv.Dotenv;

@ComponentScan(basePackages = {
		"com.example.navigation_service",
		"com.example.common.config" // WebSocketConfig 있는 곳!
})
@Import(com.example.common.config.RestTemplateConfig.class)
@SpringBootApplication
public class NavigationServiceApplication {

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.load();
		dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
		SpringApplication.run(NavigationServiceApplication.class, args);
	}

}
