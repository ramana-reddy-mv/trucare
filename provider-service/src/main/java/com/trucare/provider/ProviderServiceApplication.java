package com.trucare.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.trucare.shared.openapi.TruCareOpenApiConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(TruCareOpenApiConfiguration.class)
public class ProviderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProviderServiceApplication.class, args);
	}

}
