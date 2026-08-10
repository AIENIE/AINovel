package com.ainovel.app.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password infrastructure is deliberately independent from the HTTP filter
 * chain. Keeping this bean outside {@link SecurityConfig} prevents the admin
 * proof filter graph from forming a configuration-instantiation cycle.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordEncodingConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
