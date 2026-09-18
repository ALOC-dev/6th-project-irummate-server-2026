package com.irummate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IrummateApplication {

	public static void main(String[] args) {

		SpringApplication.run(IrummateApplication.class, args);
	}

}
