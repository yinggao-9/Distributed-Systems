/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smart.factory.grpc.repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author ying
 * LatestReadingStore
 * =============================================================================
 * A simple in-process shared singleton holding each device's latest
 * sensor reading. This is what makes subscribeAlerts (server-streaming
 * RPC) work: alert evaluation is based on the latest sensor data 
 * previously submitted via streamSensorData/analyzeDeviceHealth.
 *
 * In a real distributed deployment this would be a shared database or message queue; 
 * I use an in-JVM concurrent map to replace a database for implement my project.
 * ==============================================================================
 */
public final class LatestReadingStore {

    private static final LatestReadingStore INSTANCE = new LatestReadingStore();

    public static LatestReadingStore getInstance() {
        return INSTANCE;
    }

    // records the latest reading for a device 
    public static class Reading {
        public final float temperature;
        public final float vibration;
        public final int rpm;
        public final long timestamp;

        public Reading(float temperature, float vibration, int rpm, long timestamp) {
            this.temperature = temperature;
            this.vibration = vibration;
            this.rpm = rpm;
            this.timestamp = timestamp;
        }
    }

    private final Map<String, Reading> latestByDevice = new ConcurrentHashMap<>();

    private LatestReadingStore() {
    // Private constructor for singleton pattern.
    // All services share the same instance to read/write data.
}

    public void updateReading(String deviceId, float temperature, float vibration, int rpm, long timestamp) {
        latestByDevice.put(deviceId, new Reading(temperature, vibration, rpm, timestamp));
    }

    
    // Returns a snapshot of every device's latest reading
    public Map<String, Reading> snapshot() {
        return Map.copyOf(latestByDevice);
    }
}
