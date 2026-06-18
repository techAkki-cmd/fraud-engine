package com.fraudengine.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication
public class FraudAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudAgentApplication.class, args);
    }
}
