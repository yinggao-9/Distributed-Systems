/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smart.factory.grpc.energy;
import com.smart.factory.grpc.discovery.ServiceRegistrar;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
/**
 *
 * @author ying
 * EnergyOptimizationServer
 * =====================================================================================
 * Standalone entry point: starts EnergyOptimizationService and registers it via jmDNS.
 * =====================================================================================
 */
public class EnergyOptimizationServer {

    public static final int DEFAULT_PORT = 50053;
    public static final String INSTANCE_NAME = "EnergyOptimizationService";

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        start(port);
    }

    public static void start(int port) throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(port)
                .addService(new EnergyOptimizationServiceImpl())
                .build()
                .start();

        System.out.println("[EnergyOptimizationServer] Listening on port " + port);

        ServiceRegistrar registrar = new ServiceRegistrar();
        registrar.register(INSTANCE_NAME, port,
                "Reports power usage and receives scheduling advice (bidirectional streaming)");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[EnergyOptimizationServer] Shutting down...");
            registrar.close();
            server.shutdown();
        }));

        server.awaitTermination();
    }
}
