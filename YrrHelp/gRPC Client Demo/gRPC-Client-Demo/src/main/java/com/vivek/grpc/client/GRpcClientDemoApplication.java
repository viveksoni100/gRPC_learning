package com.vivek.grpc.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GRpcClientDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(GRpcClientDemoApplication.class, args);

		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9595).usePlaintext().build();

		// stub generate from proto
		// blocking stub will make synchronous call
		userGrpc.userBlockingStub userBlockingStub = userGrpc.newBlockingStub(channel);
		User.loginRequest loginRequest = User.loginRequest.newBuilder()
				.setUsername("vivek").setPassword("password").build();

		User.loginResponse response = userBlockingStub.login(loginRequest);
		System.out.println(response.getResponseMessage());

	}

}
