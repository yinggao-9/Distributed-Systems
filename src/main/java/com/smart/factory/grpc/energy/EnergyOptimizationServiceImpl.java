/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.smart.factory.grpc.energy;
import com.smart.factory.grpc.exception.GrpcErrorUtil;
import com.smartfactory.energy.grpc.EnergyOptimizationServiceGrpc;
import com.smartfactory.energy.grpc.PowerUsageReport;
import com.smartfactory.energy.grpc.SchedulingAdvice;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
/**
 *
 * @author ying
 * EnergyOptimizationServiceImpl
 * =============================================================================
 * Service 3 — EnergyOptimizationService
 * Bidirectional-streaming RPC implementation.
 *
 * The client and server streams are open simultaneously and independently:
 * each time the client reports a PowerUsageReport,
 * the server immediately computes advice using a fixed threshold rule 
 * and pushes back one SchedulingAdvice on its own stream —
 * neither direction blocks on the other.
 * =============================================================================
 */
public class EnergyOptimizationServiceImpl
        extends EnergyOptimizationServiceGrpc.EnergyOptimizationServiceImplBase {

    //power threshold (kW): above this, advise slowing down. 
    private static final float POWER_THRESHOLD_KW = 40.0f;

    @Override
    public StreamObserver<PowerUsageReport> reportEnergyUsage(StreamObserver<SchedulingAdvice> responseObserver) {

        ServerCallStreamObserver<SchedulingAdvice> serverObserver =
                (ServerCallStreamObserver<SchedulingAdvice>) responseObserver;

        // ---- Cancelling of messages ----
        // In a bidi stream either side may cancel early; we just log it here since there's
        // no extra background resource to release for this particular service.
        serverObserver.setOnCancelHandler(() ->
                System.out.println("[EnergyOptimization] Client cancelled the reportEnergyUsage stream."));

        return new StreamObserver<PowerUsageReport>() {
            @Override
            public void onNext(PowerUsageReport report) {
                try {
                    // input validation ----
                    GrpcErrorUtil.requireDeviceId(report.getDeviceId());
                    if (report.getPowerConsumption() < 0) {
                        throw io.grpc.Status.INVALID_ARGUMENT
                                .withDescription("powerConsumption must not be negative, got: "
                                        + report.getPowerConsumption())
                                .asRuntimeException();
                    }

                    SchedulingAdvice advice = computeAdvice(report);
                    // Push advice back right away without waiting for the client stream to
                    // finish — this is exactly what makes it "bidirectional"
                    responseObserver.onNext(advice);

                    System.out.println("[EnergyOptimization] " + report.getDeviceId()
                            + " reported " + report.getPowerConsumption() + "kW -> advice: "
                            + advice.getScheduledAction());
                } catch (Exception e) {
                    GrpcErrorUtil.reportError(responseObserver, e);
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("[EnergyOptimization] Client stream error/cancellation: " + t.getMessage());
            }

            @Override
            public void onCompleted() {
                // Client has finished reporting -> server also completes its own response stream
                responseObserver.onCompleted();
                System.out.println("[EnergyOptimization] Client finished reporting; response stream closed.");
            }
        };
    }

    //Fixed threshold rule: above threshold, advise a 10% speed reduction; otherwise no change.
    private SchedulingAdvice computeAdvice(PowerUsageReport report) {
        String action;
        float savings;
        if (report.getPowerConsumption() > POWER_THRESHOLD_KW) {
            action = "Reduce speed by 10%";
            savings = report.getPowerConsumption() * 0.10f;
        } else {
            action = "No change required";
            savings = 0.0f;
        }
        return SchedulingAdvice.newBuilder()
                .setDeviceId(report.getDeviceId())
                .setScheduledAction(action)
                .setEstimatedSavings(savings)
                .build();
    }
}
