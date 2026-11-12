package com.guild.checkout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 公会私域收银台后端入口。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PrivateCheckoutApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrivateCheckoutApplication.class, args);
    }
}
