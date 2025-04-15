package com.example.place_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import io.github.cdimascio.dotenv.Dotenv;

@Import(com.example.common.config.RestTemplateConfig.class)
@SpringBootApplication
public class PlaceServiceApplication {

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.load();
		dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

		SpringApplication.run(PlaceServiceApplication.class, args);
	}

}
