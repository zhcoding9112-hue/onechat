package com.guild.checkout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 公会私域收银台后端入口。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PrivateCheckoutApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrivateCheckoutApplication.class, args);
    }
}
