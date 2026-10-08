package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the example Spring Boot consumer and discovers its application route alongside the library auto-configuration.
 */
@SpringBootApplication
public class DemoApplication {
    /**
     * Starts the consumer Spring application with the supplied command-line arguments.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        /*
         * Delegate lifecycle ownership to Spring so the consumer and library resources shut down together.
         */
        SpringApplication.run(DemoApplication.class, args);
    }
}
