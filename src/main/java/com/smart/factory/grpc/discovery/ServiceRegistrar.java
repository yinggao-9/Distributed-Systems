/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smart.factory.grpc.discovery;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
/**
 *
 * @author ying
 * ServiceRegistrar
 * =====================================================================================
 * Uses jmDNS to advertise a gRPC service on the local network via mDNS/Zeroconf, 
 * so the GUI client can discover all available services automatically instead 
 * of hardcoding host/port.
 * Each service registers itself as a separate instance of 
 * the "_grpc._tcp.local." service type; 
 * the instance name distinguishes Monitoring / Maintenance / Energy from one another.
 * =====================================================================================
 * 
 */
public class ServiceRegistrar implements AutoCloseable {

    // shared mDNS service type for all gRPC services 
    public static final String SERVICE_TYPE = "_grpc._tcp.local.";

    private final JmDNS jmdns;
    private ServiceInfo serviceInfo;

    public ServiceRegistrar() throws IOException {
        // Bind to the local machine's reachable address; works for loopback demo setups too
        this.jmdns = JmDNS.create(InetAddress.getLocalHost());
    }

    /**
     * 
     * Registers one service instance.
     *
     * @param instanceName instance name e.g. "DeviceMonitoringService"
     * @param port         the gRPC port this service listens on
     * @param description  hort description shown to discoverers
     */
    public void register(String instanceName, int port, String description) throws IOException {
        Map<String, String> props = new HashMap<>();
        props.put("description", description);
        props.put("protocol", "grpc");

        serviceInfo = ServiceInfo.create(
                SERVICE_TYPE,
                instanceName,
                port,
                0, // weight —  unused, set to 0
                0, // priority — unused, set to 0
                props
        );

        jmdns.registerService(serviceInfo);
        System.out.println("[jmDNS] Registered " + instanceName + " on port " + port
                + " under service type " + SERVICE_TYPE);
    }

    @Override
    public void close() {
        if (serviceInfo != null) {
            jmdns.unregisterService(serviceInfo);
        }
        jmdns.unregisterAllServices();
        try {
            jmdns.close();
        } catch (IOException e) {
            System.err.println("[jmDNS] Error closing jmDNS instance: " + e.getMessage());
        }
    }
}
