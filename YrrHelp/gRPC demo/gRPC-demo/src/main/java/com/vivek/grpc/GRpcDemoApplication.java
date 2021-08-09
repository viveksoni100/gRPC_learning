package com.vivek.grpc;

import com.vivek.grpc.service.UserService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class GRpcDemoApplication {

	public static void main(String[] args) throws IOException, InterruptedException {
		SpringApplication.run(GRpcDemoApplication.class, args);

		Server server = ServerBuilder.forPort(9595).addService(new UserService()).build();
		server.start();
		System.out.println("Server started at " + server.getPort());
		server.awaitTermination();
	}

}
