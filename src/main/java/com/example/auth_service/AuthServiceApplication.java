package com.example.auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.File;

@SpringBootApplication
public class AuthServiceApplication {

	public static void main(String[] args) {
		try {
			// 여러 경로에서 .env 파일 찾기
			String[] envPaths = { ".", "..", "../.." };
			boolean envLoaded = false;

			for (String path : envPaths) {
				File envFile = new File(path + "/.env");
				if (envFile.exists()) {
					System.out.println("Loading .env file from: " + envFile.getAbsolutePath());

					Dotenv dotenv = Dotenv.configure()
							.directory(path)
							.filename(".env")
							.load();

					dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

					envLoaded = true;
					break;
				}
			}

			if (!envLoaded) {
				System.out.println("No .env file found in paths: " + String.join(", ", envPaths));
				System.out.println("Using environment variables instead");
			}

		} catch (Exception e) {
			System.out.println("Error loading .env file: " + e.getMessage());
			System.out.println("Using environment variables instead");
		}

		SpringApplication.run(AuthServiceApplication.class, args);
	}

}