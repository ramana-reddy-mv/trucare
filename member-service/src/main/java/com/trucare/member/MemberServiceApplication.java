package com.trucare.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.trucare.shared.openapi.TruCareOpenApiConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(TruCareOpenApiConfiguration.class)
public class MemberServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MemberServiceApplication.class, args);
	}

}
