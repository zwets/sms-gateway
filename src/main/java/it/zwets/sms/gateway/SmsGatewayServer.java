package it.zwets.sms.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(proxyBeanMethods = false)
public class SmsGatewayServer {

	public static void main(String[] args) {
		SpringApplication.run(SmsGatewayServer.class, args);
	}
}
