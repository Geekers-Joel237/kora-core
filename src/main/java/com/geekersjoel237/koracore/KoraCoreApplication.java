package com.geekersjoel237.koracore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class KoraCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(KoraCoreApplication.class, args);
    }
}