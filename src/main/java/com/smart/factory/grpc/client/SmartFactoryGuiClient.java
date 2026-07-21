/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smart.factory.grpc.client;
import com.smartfactory.discovery.ServiceDiscoveryClient;
import com.smartfactory.energy.grpc.EnergyOptimizationServiceGrpc;
import com.smartfactory.energy.grpc.PowerUsageReport;
import com.smartfactory.energy.grpc.SchedulingAdvice;
import com.smartfactory.maintenance.grpc.*;
import com.smartfactory.monitoring.grpc.DeviceMonitoringServiceGrpc;
import com.smartfactory.monitoring.grpc.SensorReading;
import com.smartfactory.monitoring.grpc.StreamAck;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author ying
 * SmartFactoryGuiClient
 * =============================================================================
 * The GUI client for the Smart Factory system 
 *
 * three required capabilities:
 *   1) Viewing    — (presentation/discovery of services)
 *   2) Control    — (passing parameters)
 *   3) Invocation — (invocation, viewing results)
 
 * A single client window controls all three services, one tab each.
 * =============================================================================
 * 
 */
public class SmartFactoryGuiClient {
    
}
