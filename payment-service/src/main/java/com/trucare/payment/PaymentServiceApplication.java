package com.trucare.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.trucare.shared.openapi.TruCareOpenApiConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(TruCareOpenApiConfiguration.class)
@EnableFeignClients
@EnableScheduling
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
