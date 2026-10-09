package com.jobforge.backend;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** JobForge modular monolith entry point. All timestamps are UTC. */
@SpringBootApplication
public class JobForgeApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(JobForgeApplication.class, args);
    }
}
