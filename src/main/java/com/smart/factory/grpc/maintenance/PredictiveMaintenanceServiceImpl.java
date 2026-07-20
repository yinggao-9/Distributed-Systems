/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smart.factory.grpc.maintenance;
import com.smart.factory.grpc.exception.GrpcErrorUtil;
import com.smart.factory.grpc.repository.LatestReadingStore;
import com.smartfactory.maintenance.grpc.*;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 *
 * @author ying
 * 
 * PredictiveMaintenanceServiceImpl
 * =============================================================================
 * Service 2 — PredictiveMaintenanceService
 * implements TWO RPC styles:
 *   1) analyzeDeviceHealth — Unary RPC
 *   2) subscribeAlerts     — Server-streaming RPC
 * =============================================================================
 */
public class PredictiveMaintenanceServiceImpl
        extends PredictiveMaintenanceServiceGrpc.PredictiveMaintenanceServiceImplBase {

    
    // Background scheduler used by subscribeAlerts to periodically scan the latest readings
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // -----------------------------------------------------------------
    // RPC 1: analyzeDeviceHealth (Unary)
    // -----------------------------------------------------------------
    @Override
    public void analyzeDeviceHealth(DeviceReadingRequest request, StreamObserver<HealthAssessment> responseObserver) {
        try {
            // ---- input validation ----
            GrpcErrorUtil.requireDeviceId(request.getDeviceId());
            GrpcErrorUtil.requireNonNegative(request.getRpm(), "rpm");

            // ---- Deadline demonstration ----
            // If the client's deadline has already expired, Context.current() reflects that;
            // check before doing real work so we don't waste server resources on a request
            // the client has already given up waiting for.
            if (Context.current().isCancelled()) {
                responseObserver.onError(Status.CANCELLED
                        .withDescription("Request deadline exceeded or call was cancelled by the client.")
                        .asRuntimeException());
                return;
            }

            // ---- simple rule-based health scoring ----
            HealthAssessment assessment = computeHealth(
                    request.getTemperature(), request.getVibration(), request.getRpm());

          
            // Persist this submitted reading too, since subscribeAlerts consumes it
            LatestReadingStore.getInstance().updateReading(
                    request.getDeviceId(), request.getTemperature(),
                    request.getVibration(), request.getRpm(), request.getTimestamp());

            responseObserver.onNext(assessment);
            responseObserver.onCompleted();
        } catch (Exception e) {
            GrpcErrorUtil.reportError(responseObserver, e);
        }
    }

    
    //Rule-based health scoring: higher temperature/vibration => lower health score. 
    private HealthAssessment computeHealth(float temperature, float vibration, int rpm) {
        //total score is 100.
        int score = 100;
        //penalize above 40°C, For every 1°C exceeded, 1.5 points will be deducted.
        score -= Math.max(0, (int) ((temperature - 40) * 1.5)); 
        score -= Math.max(0, (int) (vibration * 8));            //  penalize vibration
        score = Math.max(0, Math.min(100, score));

        String riskLevel;
        String suggestion;
        if (score >= 70) {
            riskLevel = "Normal";
            suggestion = "No action needed; continue routine monitoring.";
        } else if (score >= 40) {
            riskLevel = "Warning";
            suggestion = "Schedule bearing inspection within 24 hours.";
        } else {
            riskLevel = "Critical";
            suggestion = "Stop the machine and perform immediate maintenance.";
        }

        return HealthAssessment.newBuilder()
                .setHealthScore(score)
                .setRiskLevel(riskLevel)
                .setSuggestion(suggestion)
                .build();
    }

    // -----------------------------------------------------------------
    // RPC 2: subscribeAlerts (Server Streaming)
    // -----------------------------------------------------------------
    @Override
    public void subscribeAlerts(AlertSubscriptionRequest request, StreamObserver<AlertMessage> responseObserver) {
        try {
            GrpcErrorUtil.requireNonNegative(request.getThreshold(), "threshold");
        } catch (Exception e) {
            GrpcErrorUtil.reportError(responseObserver, e);
            return;
        }

        int threshold = request.getThreshold();
        // Cast to ServerCallStreamObserver so we can detect cancellation
        ServerCallStreamObserver<AlertMessage> serverObserver =
                (ServerCallStreamObserver<AlertMessage>) responseObserver;

        AtomicBoolean cancelled = new AtomicBoolean(false);

        
        // Every 2 seconds, scan the latest readings; push an alert for any device below threshold
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            if (cancelled.get()) return;
            try {
                Map<String, LatestReadingStore.Reading> snapshot = LatestReadingStore.getInstance().snapshot();
                for (Map.Entry<String, LatestReadingStore.Reading> entry : snapshot.entrySet()) {
                    LatestReadingStore.Reading r = entry.getValue();
                    int score = 100
                            - Math.max(0, (int) ((r.temperature - 40) * 1.5))
                            - Math.max(0, (int) (r.vibration * 8));
                    if (score < threshold) {
                        AlertMessage alert = AlertMessage.newBuilder()
                                .setDeviceId(entry.getKey())
                                .setAlertMessage("Health score " + score + " fell below threshold " + threshold
                                        + " — critical vibration/temperature detected, inspect immediately.")
                                .setTimestamp(System.currentTimeMillis())
                                .build();
                        responseObserver.onNext(alert);
                    }
                }
            } catch (Exception e) {
                // An error while pushing shouldn't crash the background thread — just log it
                System.err.println("[PredictiveMaintenance] Error during alert scan: " + e.getMessage());
            }
        }, 0, 2, TimeUnit.SECONDS);

        // Cancelling of messages ----
        // When the client cancels the subscription (e.g. closes the GUI window),
        // the server MUST stop the background task, or it leaks a thread/resource.
        serverObserver.setOnCancelHandler(() -> {
            cancelled.set(true);
            future.cancel(true);
            System.out.println("[PredictiveMaintenance] subscribeAlerts cancelled by client, background task stopped.");
        });
    }
}
