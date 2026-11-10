package com.trucare.claims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import com.trucare.claims.client.PolicyClient;
import com.trucare.shared.client.MemberClient;
import com.trucare.shared.client.ProviderClient;


@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(clients = {MemberClient.class, ProviderClient.class, PolicyClient.class})
public class ClaimsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClaimsServiceApplication.class, args);
	}
}
