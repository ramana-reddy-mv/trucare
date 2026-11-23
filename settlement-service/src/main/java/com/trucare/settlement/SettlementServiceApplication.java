package com.trucare.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import com.trucare.shared.openapi.TruCareOpenApiConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(TruCareOpenApiConfiguration.class)
@EnableFeignClients
public class SettlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
