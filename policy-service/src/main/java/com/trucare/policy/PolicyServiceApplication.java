package com.trucare.policy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import com.trucare.shared.client.MemberClient;

@SpringBootApplication
@EnableFeignClients(clients = MemberClient.class)
public class PolicyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyServiceApplication.class, args);
    }
}
