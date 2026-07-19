/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smart.factory.grpc.exception;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

/**
 *
 * @author ying
 * 
 * A shared utility for the "grpc Error Handling"
 * 1.Appropriate error handling
 * 2.Cancelling of messages
 * 3.Appropriate use of Metadata,Authentication etc
 */
public class GrpcErrorUtil {
     
    //Metadata key: a simple "auth token" header used to demonstrate Authentication.
    public static final Metadata.Key<String> AUTH_TOKEN_KEY =
            Metadata.Key.of("auth-token", Metadata.ASCII_STRING_MARSHALLER);

    //A fixed shared "secret" for demo purposes;
    //in real word, it should be replaced with a real auth token
    public static final String EXPECTED_AUTH_TOKEN = "smart-factory-demo-token";
    
    /**
     * Validates that a deviceId field is present; if not, builds one
     * StatusRuntimeException with INVALID_ARGUMENT and a clear message.
     * @param deviceId device ID, must not be null or blank
     * @throws StatusRuntimeException when deviceId is null or blank
     */
    public static void requireDeviceId(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("deviceId must not be empty — every reading needs to identify its machine.")
                    .asRuntimeException();
        }
    }

    /**
     * Validates a numeric field is not obviously out of range.
     * Simple example used across services: rpm must not be negative.
     */
    public static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw Status.INVALID_ARGUMENT
                    .withDescription(fieldName + " must not be negative, got: " + value)
                    .asRuntimeException();
        }
    }

    /**
     * Checks the auth-token carried in request Metadata.
     * On failure, throws an UNAUTHENTICATED status.
     */
    public static void checkAuthToken(Metadata headers) {
        String token = headers.get(AUTH_TOKEN_KEY);
        if (token == null || !token.equals(EXPECTED_AUTH_TOKEN)) {
            throw Status.UNAUTHENTICATED
                    .withDescription("Missing or invalid auth-token metadata header.")
                    .asRuntimeException();
        }
    }

    /**
     * A single place that converts any thrown exception into an
     * appropriate gRPC Status and reports it via onError, while
     * preserving the original message for debuggability.
     */
    public static void reportError(StreamObserver<?> responseObserver, Throwable t) {
        if (t instanceof StatusRuntimeException) {
            // Already a well-formed gRPC status, forward as-is
            responseObserver.onError(t);
        } else {
            // Unexpected exception -> wrap as INTERNAL,preserving the error message
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Unexpected server error: " + t.getMessage())
                            .withCause(t)
                            .asRuntimeException());
        }
    }

    /**
     * Convenience factory the CLIENT side uses to attach the auth-token
     * metadata header to every outgoing call via MetadataUtils.
     */
    public static Metadata buildAuthMetadata() {
        Metadata metadata = new Metadata();
        metadata.put(AUTH_TOKEN_KEY, EXPECTED_AUTH_TOKEN);
        return metadata;
    }
    
}
