package com.router;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RouterApplication {
    public static void main(String[] args) {
        SpringApplication.run(RouterApplication.class, args);
    }
}
