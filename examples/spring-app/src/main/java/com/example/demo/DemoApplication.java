package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the example Spring Boot consumer. Spring discovers routes and account
 * configuration in the routes and auth subpackages. Geshra supplies its web server through
 * auto-configuration, so the application needs no manual library package scan or server setup.
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
