/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smart.factory.grpc.maintenance;
import com.smart.factory.grpc.discovery.ServiceRegistrar;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
/**
 *
 * @author ying
 * PredictiveMaintenanceServer
 * =======================================================================================
 * Standalone entry point: starts PredictiveMaintenanceService and registers it via jmDNS.
 * =======================================================================================
 */
public class PredictiveMaintenanceServer {

    public static final int DEFAULT_PORT = 50052;
    public static final String INSTANCE_NAME = "PredictiveMaintenanceService";

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        start(port);
    }

    public static void start(int port) throws IOException, InterruptedException {
        Server server = ServerBuilder.forPort(port)
                .addService(new PredictiveMaintenanceServiceImpl())
                .build()
                .start();

        System.out.println("[PredictiveMaintenanceServer] Listening on port " + port);

        ServiceRegistrar registrar = new ServiceRegistrar();
        registrar.register(INSTANCE_NAME, port,
                "Analyzes device health (unary) and streams alerts (server streaming)");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[PredictiveMaintenanceServer] Shutting down...");
            registrar.close();
            server.shutdown();
        }));

        server.awaitTermination();
    }
}
