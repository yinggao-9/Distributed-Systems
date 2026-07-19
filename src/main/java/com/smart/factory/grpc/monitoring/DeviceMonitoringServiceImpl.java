/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smart.factory.grpc.monitoring;
import com.smart.factory.grpc.repository.LatestReadingStore;
import com.smartfactory.monitoring.grpc.DeviceMonitoringServiceGrpc;
import com.smart.factory.grpc.exception.GrpcErrorUtil;
import com.smartfactory.monitoring.grpc.SensorReading;
import com.smartfactory.monitoring.grpc.StreamAck;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.atomic.AtomicInteger;
/**
 *
 * @author ying
 * 
 *  DeviceMonitoringServiceImpl
 * =============================================================================
 * Service 1 — DeviceMonitoringService（client stream RPC ）
 *
 * The client repeatedly calls onNext(SensorReading) to push readings.
 * The server records each reading (and also writes it into the shared
 * LatestReadingStore so PredictiveMaintenanceService can later read
 * the "latest reading"). Only once the client calls onCompleted()
 * does the server reply with a single StreamAck.
 * =============================================================================
 */
public class DeviceMonitoringServiceImpl extends DeviceMonitoringServiceGrpc.DeviceMonitoringServiceImplBase {

    @Override
    public StreamObserver<SensorReading> streamSensorData(StreamObserver<StreamAck> responseObserver) {
        // A fresh counter per call, since one service instance may serve many concurrent client streams
        AtomicInteger receivedCount = new AtomicInteger(0);

        return new StreamObserver<SensorReading>() {

            @Override
            public void onNext(SensorReading reading) {
                try {
                    // input validation ----
                    GrpcErrorUtil.requireDeviceId(reading.getDeviceId());
                    GrpcErrorUtil.requireNonNegative(reading.getRpm(), "rpm");

                    receivedCount.incrementAndGet();

                   
                    // Persist the latest reading into shared memory for subscribeAlerts to consume
                    LatestReadingStore.getInstance().updateReading(
                            reading.getDeviceId(),
                            reading.getTemperature(),
                            reading.getVibration(),
                            reading.getRpm(),
                            reading.getTimestamp()
                    );

                    System.out.println("[DeviceMonitoring] Received reading from " + reading.getDeviceId()
                            + " (temp=" + reading.getTemperature() + "C, vib=" + reading.getVibration()
                            + "mm/s, rpm=" + reading.getRpm() + ")");
                } catch (Exception e) {
                    // Validation failure: end the whole stream immediately with a proper status
                    // instead of letting the server crash.
                    GrpcErrorUtil.reportError(responseObserver, e);
                }
            }

            @Override
            public void onError(Throwable t) {
                // Reached when the client cancels or a network error occurs — just log it,
                // no further response is expected once the stream is already broken.
                System.err.println("[DeviceMonitoring] Client stream error/cancellation: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                // Client has finished sending all readings -> now reply once with a StreamAck
                StreamAck ack = StreamAck.newBuilder()
                        .setStatus("success")
                        .setReceivedCount(receivedCount.get())
                        .build();
                responseObserver.onNext(ack);
                responseObserver.onCompleted();
                System.out.println("[DeviceMonitoring] Stream completed, total received=" + receivedCount.get());
            }
        };
    }
}
