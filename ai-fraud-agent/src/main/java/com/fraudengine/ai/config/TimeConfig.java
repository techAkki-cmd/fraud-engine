package com.fraudengine.ai.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock fraudAgentClock() {
        return Clock.systemUTC();
    }
}
