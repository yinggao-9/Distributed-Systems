/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smart.factory.grpc.discovery;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 *
 * @author ying
 * ServiceDiscoveryClient
 * =============================================================================
 * Used by the GUI client: listens for gRPC services appearing or
 * disappearing on the mDNS network, and keeps a live map of
 * "instance name -> (host, port)".
 * =============================================================================
 */
public class ServiceDiscoveryClient implements AutoCloseable {

    //a discovered service's address 
    public static class DiscoveredService {
        public final String host;
        public final int port;

        public DiscoveredService(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    private final JmDNS jmdns;
    // Thread-safe map: jmDNS callbacks fire on a background thread while the GUI thread reads it too
    private final Map<String, DiscoveredService> discovered = new ConcurrentHashMap<>();

    public ServiceDiscoveryClient() throws IOException {
        this.jmdns = JmDNS.create(InetAddress.getLocalHost());
        jmdns.addServiceListener(ServiceRegistrar.SERVICE_TYPE, new ServiceListener() {
            @Override
            public void serviceAdded(ServiceEvent event) {
                // Service was just found but not yet resolved (IP/port) — request resolution
                jmdns.requestServiceInfo(event.getType(), event.getName(), 1000);
            }

            @Override
            public void serviceRemoved(ServiceEvent event) {
                discovered.remove(event.getName());
                System.out.println("[jmDNS] Service removed: " + event.getName());
            }

            @Override
            public void serviceResolved(ServiceEvent event) {
                ServiceInfo info = event.getInfo();
                if (info.getInet4Addresses().length > 0) {
                    String host = info.getInet4Addresses()[0].getHostAddress();
                    int port = info.getPort();
                    discovered.put(event.getName(), new DiscoveredService(host, port));
                    System.out.println("[jmDNS] Discovered " + event.getName() + " at " + host + ":" + port);
                }
            }
        });
    }

    //Returns a snapshot of currently discovered services (instance name -> DiscoveredService) 
    public Map<String, DiscoveredService> getDiscoveredServices() {
        return new ConcurrentHashMap<>(discovered);
    }

    @Override
    public void close() {
        jmdns.unregisterAllServices();
        try {
            jmdns.close();
        } catch (IOException e) {
            System.err.println("[jmDNS] Error closing discovery client: " + e.getMessage());
        }
    }
}
