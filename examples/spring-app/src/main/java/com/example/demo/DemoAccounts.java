package com.example.demo;

import geshra.net.web.auth.DefaultAuthenticator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Optional demo account, created only when the application is explicitly given demo.password.
 * The library itself ships no users or default credentials.
 */
@Configuration
public class DemoAccounts {
    /**
     * Creates the example's in-memory user store.
     * @param password optional password supplied at application startup
     * @return example authenticator
     */
    @Bean
    public DefaultAuthenticator demoAuthenticator(@Value("${demo.password:}") String password) {
        DefaultAuthenticator users = new DefaultAuthenticator();
        if (!password.isEmpty()) users.register("demo", password, "member");
        return users;
    }
}
