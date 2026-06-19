package com.fraudengine.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@EnableConfigServer
@SpringBootApplication
public class FraudEngineConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudEngineConfigServerApplication.class, args);
    }
}
