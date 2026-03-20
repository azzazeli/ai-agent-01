package com.alexm.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentApplication {

	public static void main(String[] args) {
		System.out.println("Starting application ...");
		SpringApplication.run(AgentApplication.class, args);
	}

}
