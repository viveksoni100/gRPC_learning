package com.vivek.grpc.service;

import com.vivek.grpc.User;
import com.vivek.grpc.userGrpc;
import io.grpc.stub.StreamObserver;

/**
 * @author viveksoni100
 */
public class UserService extends userGrpc.userImplBase {

    @Override
    public void login(User.loginRequest request, StreamObserver<User.loginResponse> responseObserver) {

        System.out.println("Inside login . . .");
        String username = request.getUsername();
        String password = request.getPassword();

        User.loginResponse.Builder response = User.loginResponse.newBuilder();

        if (username.equals(password)) {
            response.setResponseCode(200).setResponseMessage("SUCCESS");
        } else {
            response.setResponseCode(500).setResponseMessage("GO TO HELL");
        }

        // wrap the response object in observer and send
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void logout(User.empty request, StreamObserver<User.loginResponse> responseObserver) {

    }
}
