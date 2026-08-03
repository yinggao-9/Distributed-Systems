/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smart.factory.grpc.client;
import com.smart.factory.grpc.discovery.ServiceDiscoveryClient;
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
public class SmartFactoryGuiClient extends JFrame {

    private ServiceDiscoveryClient discoveryClient;
    // instance name -> already-open ManagedChannel (reuse connections, avoid reconnecting each call)
    private final Map<String, ManagedChannel> channelCache = new java.util.concurrent.ConcurrentHashMap<>();

    
    private JComboBox<String> monitoringHostBox;
    private JComboBox<String> maintenanceHostBox;
    private JComboBox<String> energyHostBox;

    public SmartFactoryGuiClient() {
        super("Smart Factory Control Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(880, 640);
        setLocationRelativeTo(null);

        try {
            discoveryClient = new ServiceDiscoveryClient();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to start jmDNS discovery: " + e.getMessage());
        }

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Service Discovery", buildDiscoveryTab());
        tabs.addTab("Device Monitoring (client streaming)", buildMonitoringTab());
        tabs.addTab("Predictive Maintenance (unary + server streaming)", buildMaintenanceTab());
        tabs.addTab("Energy Optimization (bidirectional streaming)", buildEnergyTab());
        add(tabs);
    }

    
    //Reuse or create a gRPC Channel to a discovered service instance 
    private ManagedChannel getChannelFor(String instanceName, String hostPortLabel) {
        return channelCache.computeIfAbsent(instanceName + "@" + hostPortLabel, k -> {
            String[] parts = hostPortLabel.split(":");
            return ManagedChannelBuilder.forAddress(parts[0], Integer.parseInt(parts[1]))
                    .usePlaintext()
                    .build();
        });
    }

    // =====================================================================
    // Tab 0: Service Discovery — Viewing
    // =====================================================================
    private JPanel buildDiscoveryTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> serviceList = new JList<>(listModel);
        panel.add(new JScrollPane(serviceList), BorderLayout.CENTER);

        JButton scanButton = new JButton("Scan network for gRPC services (jmDNS)");
        scanButton.addActionListener(e -> {
            listModel.clear();
            Map<String, ServiceDiscoveryClient.DiscoveredService> found = discoveryClient.getDiscoveredServices();
            if (found.isEmpty()) {
                listModel.addElement("No services found yet — start the servers, wait a few seconds, then scan again.");
            }
            for (Map.Entry<String, ServiceDiscoveryClient.DiscoveredService> entry : found.entrySet()) {
                listModel.addElement(entry.getKey() + "  ->  " + entry.getValue());
            }
            refreshHostBoxes(found);
        });

        JLabel info = new JLabel("<html>Uses jmDNS to discover services advertised as _grpc._tcp.local.<br>"
                + "Run the three servers first, then click Scan. Discovered addresses populate the<br>"
                + "dropdowns on the other tabs; if none are found, each tab falls back to localhost defaults.</html>");
        panel.add(info, BorderLayout.NORTH);
        panel.add(scanButton, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshHostBoxes(Map<String, ServiceDiscoveryClient.DiscoveredService> found) {
        updateBox(monitoringHostBox, found.get("DeviceMonitoringService"), "localhost:50051");
        updateBox(maintenanceHostBox, found.get("PredictiveMaintenanceService"), "localhost:50052");
        updateBox(energyHostBox, found.get("EnergyOptimizationService"), "localhost:50053");
    }

    private void updateBox(JComboBox<String> box, ServiceDiscoveryClient.DiscoveredService discovered, String fallback) {
        if (box == null) return;
        box.removeAllItems();
        box.addItem(discovered != null ? discovered.toString() : fallback);
    }

    // =====================================================================
    // Tab 1: Device Monitoring — client streaming — Control + Invocation
    // =====================================================================
    private JPanel buildMonitoringTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        monitoringHostBox = new JComboBox<>(new String[]{"localhost:50051"});

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        JTextField deviceIdField = new JTextField("001");
        JTextField tempField = new JTextField("45.5");
        JTextField vibField = new JTextField("2.1");
        JTextField rpmField = new JTextField("1500");
        form.add(new JLabel("Discovered address:")); form.add(monitoringHostBox);
        form.add(new JLabel("Device ID:")); form.add(deviceIdField);
        form.add(new JLabel("Temperature (°C):")); form.add(tempField);
        form.add(new JLabel("Vibration (mm/s):")); form.add(vibField);
        form.add(new JLabel("RPM:")); form.add(rpmField);

        JTextArea log = new JTextArea();
        log.setEditable(false);

        
        // A client-streaming call needs one requestObserver kept open for the whole session
        AtomicReference<StreamObserver<SensorReading>> requestObserverRef = new AtomicReference<>();

        JButton startButton = new JButton("Start streaming session");
        JButton sendButton = new JButton("Send reading");
        JButton finishButton = new JButton("Finish stream & get Ack");
        sendButton.setEnabled(false);
        finishButton.setEnabled(false);

        startButton.addActionListener(e -> {
            ManagedChannel channel = getChannelFor("DeviceMonitoringService",
                    (String) monitoringHostBox.getSelectedItem());
            DeviceMonitoringServiceGrpc.DeviceMonitoringServiceStub stub =
                    DeviceMonitoringServiceGrpc.newStub(channel);

            StreamObserver<StreamAck> responseObserver = new StreamObserver<StreamAck>() {
                @Override
                public void onNext(StreamAck value) {
                    SwingUtilities.invokeLater(() -> log.append("Server ack: status=" + value.getStatus()
                            + ", receivedCount=" + value.getReceivedCount() + "\n"));
                }
                @Override
                public void onError(Throwable t) {
                    SwingUtilities.invokeLater(() -> log.append("ERROR: " + t.getMessage() + "\n"));
                }
                @Override
                public void onCompleted() {
                    SwingUtilities.invokeLater(() -> log.append("Stream closed by server.\n"));
                }
            };
            requestObserverRef.set(stub.streamSensorData(responseObserver));
            log.append("Streaming session started.\n");
            sendButton.setEnabled(true);
            finishButton.setEnabled(true);
            startButton.setEnabled(false);
        });

        sendButton.addActionListener(e -> {
            StreamObserver<SensorReading> obs = requestObserverRef.get();
            if (obs == null) return;
            try {
                SensorReading reading = SensorReading.newBuilder()
                        .setDeviceId(deviceIdField.getText())
                        .setTimestamp(System.currentTimeMillis())
                        .setTemperature(Float.parseFloat(tempField.getText()))
                        .setVibration(Float.parseFloat(vibField.getText()))
                        .setRpm(Integer.parseInt(rpmField.getText()))
                        .build();
                obs.onNext(reading);
                log.append("Sent reading for device " + deviceIdField.getText() + "\n");
            } catch (NumberFormatException ex) {
                log.append("Invalid number in one of the fields.\n");
            }
        });

        finishButton.addActionListener(e -> {
            StreamObserver<SensorReading> obs = requestObserverRef.getAndSet(null);
            if (obs != null) {
                obs.onCompleted();
                log.append("Stream marked complete, waiting for server ack...\n");
            }
            sendButton.setEnabled(false);
            finishButton.setEnabled(false);
            startButton.setEnabled(true);
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(startButton); buttons.add(sendButton); buttons.add(finishButton);

        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(log), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    // =====================================================================
    // Tab 2: Predictive Maintenance — unary + server streaming
    // =====================================================================
    private JPanel buildMaintenanceTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        maintenanceHostBox = new JComboBox<>(new String[]{"localhost:50052"});

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        JTextField deviceIdField = new JTextField("002");
        JTextField tempField = new JTextField("58.0");
        JTextField vibField = new JTextField("4.6");
        JTextField rpmField = new JTextField("1400");
        JTextField thresholdField = new JTextField("50");
        form.add(new JLabel("Discovered address:")); form.add(maintenanceHostBox);
        form.add(new JLabel("Device ID:")); form.add(deviceIdField);
        form.add(new JLabel("Temperature (°C):")); form.add(tempField);
        form.add(new JLabel("Vibration (mm/s):")); form.add(vibField);
        form.add(new JLabel("RPM:")); form.add(rpmField);
        form.add(new JLabel("Alert threshold (health score):")); form.add(thresholdField);

        JTextArea log = new JTextArea();
        log.setEditable(false);

        JButton analyzeButton = new JButton("Analyze device health (unary)");
        JButton subscribeButton = new JButton("Subscribe to alerts (server streaming)");
        JButton cancelButton = new JButton("Cancel subscription");
        cancelButton.setEnabled(false);

        AtomicReference<io.grpc.stub.ClientCallStreamObserver<?>> activeCallRef = new AtomicReference<>();

        analyzeButton.addActionListener(e -> {
            try {
                ManagedChannel channel = getChannelFor("PredictiveMaintenanceService",
                    (String) maintenanceHostBox.getSelectedItem());
                // Demonstrates deadlines: this call fails automatically if it takes over 3 seconds
                PredictiveMaintenanceServiceGrpc.PredictiveMaintenanceServiceBlockingStub stub =
                        PredictiveMaintenanceServiceGrpc.newBlockingStub(channel)
                                .withDeadlineAfter(3, TimeUnit.SECONDS);

                DeviceReadingRequest request = DeviceReadingRequest.newBuilder()
                        .setDeviceId(deviceIdField.getText())
                        .setTimestamp(System.currentTimeMillis())
                        .setTemperature(Float.parseFloat(tempField.getText()))
                        .setVibration(Float.parseFloat(vibField.getText()))
                        .setRpm(Integer.parseInt(rpmField.getText()))
                        .build();

                HealthAssessment result = stub.analyzeDeviceHealth(request);
                log.append("Health score=" + result.getHealthScore()
                        + ", risk=" + result.getRiskLevel()
                        + ", suggestion=" + result.getSuggestion() + "\n");
            } catch (Exception ex) {
                log.append("ERROR: " + ex.getMessage() + "\n");
            }
        });

        subscribeButton.addActionListener(e -> {
            ManagedChannel channel = getChannelFor("PredictiveMaintenanceService",
                    (String) maintenanceHostBox.getSelectedItem());
            PredictiveMaintenanceServiceGrpc.PredictiveMaintenanceServiceStub stub =
                    PredictiveMaintenanceServiceGrpc.newStub(channel);

            AlertSubscriptionRequest request = AlertSubscriptionRequest.newBuilder()
                    .setThreshold(Integer.parseInt(thresholdField.getText()))
                    .build();

            io.grpc.stub.StreamObserver<AlertMessage> responseObserver = new io.grpc.stub.StreamObserver<AlertMessage>() {
                @Override
                public void onNext(AlertMessage value) {
                    SwingUtilities.invokeLater(() -> log.append("ALERT for " + value.getDeviceId()
                            + ": " + value.getAlertMessage() + "\n"));
                }
                @Override
                public void onError(Throwable t) {
                    SwingUtilities.invokeLater(() -> log.append("Alert stream ended: " + t.getMessage() + "\n"));
                }
                @Override
                public void onCompleted() {
                    SwingUtilities.invokeLater(() -> log.append("Alert stream completed by server.\n"));
                }
            };

            // ClientResponseObserver 不是必须的；这里直接用异步 stub 调用即可拿到可取消的 Call
            stub.subscribeAlerts(request, new io.grpc.stub.ClientResponseObserver<AlertSubscriptionRequest, AlertMessage>() {
                @Override
                public void beforeStart(io.grpc.stub.ClientCallStreamObserver<AlertSubscriptionRequest> requestStream) {
                    activeCallRef.set(requestStream);
                }

                @Override
                public void onNext(AlertMessage value) {
                    responseObserver.onNext(value);
                }

                @Override
                public void onError(Throwable t) {
                    responseObserver.onError(t);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            });
            log.append("Subscribed to alerts with threshold=" + thresholdField.getText() + "\n");
            cancelButton.setEnabled(true);
            subscribeButton.setEnabled(false);
        });

        cancelButton.addActionListener(e -> {
            io.grpc.stub.ClientCallStreamObserver<?> call = activeCallRef.getAndSet(null);
            if (call != null) {
                call.cancel("User cancelled subscription", null);
                log.append("Subscription cancelled.\n");
            }
            subscribeButton.setEnabled(true);
            cancelButton.setEnabled(false);
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(analyzeButton); buttons.add(subscribeButton); buttons.add(cancelButton);

        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(log), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    // =====================================================================
    // Tab 3: Energy Optimization — bidirectional streaming
    // =====================================================================
    private JPanel buildEnergyTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        energyHostBox = new JComboBox<>(new String[]{"localhost:50053"});

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        JTextField deviceIdField = new JTextField("Motor-02");
        JTextField powerField = new JTextField("45.0");
        form.add(new JLabel("Discovered address:")); form.add(energyHostBox);
        form.add(new JLabel("Device ID:")); form.add(deviceIdField);
        form.add(new JLabel("Power consumption (kW):")); form.add(powerField);

        JTextArea log = new JTextArea();
        log.setEditable(false);

        AtomicReference<StreamObserver<PowerUsageReport>> requestObserverRef = new AtomicReference<>();

        JButton startButton = new JButton("Start bidirectional session");
        JButton sendButton = new JButton("Report power usage");
        JButton endButton = new JButton("End session");
        sendButton.setEnabled(false);
        endButton.setEnabled(false);

        startButton.addActionListener(e -> {
            ManagedChannel channel = getChannelFor("EnergyOptimizationService",
                    (String) energyHostBox.getSelectedItem());
            EnergyOptimizationServiceGrpc.EnergyOptimizationServiceStub stub =
                    EnergyOptimizationServiceGrpc.newStub(channel);

            StreamObserver<SchedulingAdvice> responseObserver = new StreamObserver<SchedulingAdvice>() {
                @Override
                public void onNext(SchedulingAdvice value) {
                    SwingUtilities.invokeLater(() -> log.append("Advice for " + value.getDeviceId()
                            + ": " + value.getScheduledAction()
                            + " (est. savings " + value.getEstimatedSavings() + " kWh)\n"));
                }
                @Override
                public void onError(Throwable t) {
                    SwingUtilities.invokeLater(() -> log.append("ERROR: " + t.getMessage() + "\n"));
                }
                @Override
                public void onCompleted() {
                    SwingUtilities.invokeLater(() -> log.append("Session closed by server.\n"));
                }
            };
            requestObserverRef.set(stub.reportEnergyUsage(responseObserver));
            log.append("Bidirectional session started.\n");
            sendButton.setEnabled(true);
            endButton.setEnabled(true);
            startButton.setEnabled(false);
        });

        sendButton.addActionListener(e -> {
            StreamObserver<PowerUsageReport> obs = requestObserverRef.get();
            if (obs == null) return;
            try {
                PowerUsageReport report = PowerUsageReport.newBuilder()
                        .setDeviceId(deviceIdField.getText())
                        .setPowerConsumption(Float.parseFloat(powerField.getText()))
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                obs.onNext(report);
                log.append("Reported " + powerField.getText() + "kW for " + deviceIdField.getText() + "\n");
            } catch (NumberFormatException ex) {
                log.append("Invalid power value.\n");
            }
        });

        endButton.addActionListener(e -> {
            StreamObserver<PowerUsageReport> obs = requestObserverRef.getAndSet(null);
            if (obs != null) {
                obs.onCompleted();
                log.append("Client ended its stream.\n");
            }
            sendButton.setEnabled(false);
            endButton.setEnabled(false);
            startButton.setEnabled(true);
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(startButton); buttons.add(sendButton); buttons.add(endButton);

        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(log), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SmartFactoryGuiClient().setVisible(true));
    }
}
