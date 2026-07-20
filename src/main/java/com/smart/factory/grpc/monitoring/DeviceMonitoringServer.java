/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smart.factory.grpc.monitoring;

import com.smart.factory.grpc.discovery.ServiceRegistrar;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
/**
 *
 * @author ying
 * DeviceMonitoringServer
 * =============================================================================
 * Standalone entry point: starts the DeviceMonitoringService gRPC
 * server and registers it on the LAN via jmDNS so the GUI client can auto-discover it.
 * =============================================================================
 */
public class DeviceMonitoringServer {

    public static final int DEFAULT_PORT = 50051;
    public static final String INSTANCE_NAME = "DeviceMonitoringService";

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        start(port);
    }

    //reusable by AllServicesLauncher or run standalone 
    public static void start(int port) throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(port)
                .addService(new DeviceMonitoringServiceImpl())
                .build()
                .start();

        System.out.println("[DeviceMonitoringServer] Listening on port " + port);

        
        // Register with jmDNS so the GUI client can discover this instance
        ServiceRegistrar registrar = new ServiceRegistrar();
        registrar.register(INSTANCE_NAME, port, "Streams sensor data from factory devices (client streaming)");

        
        // Ensure graceful shutdown of both the gRPC server and jmDNS registration on JVM exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[DeviceMonitoringServer] Shutting down...");
            registrar.close();
            server.shutdown();
        }));

        server.awaitTermination();
    }
}
