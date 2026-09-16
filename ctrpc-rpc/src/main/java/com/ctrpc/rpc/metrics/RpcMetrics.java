package com.ctrpc.rpc.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/** Micrometer metrics for RPC client and server calls. */
public final class RpcMetrics {
    private final MeterRegistry registry;

    public RpcMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void recordClient(Timer.Sample sample, String service, String method, boolean success) {
        record(sample, "client", service, method, success);
    }

    public void recordServer(Timer.Sample sample, String service, String method, boolean success) {
        record(sample, "server", service, method, success);
    }

    public void increment(String side, String service, String method, String outcome) {
        Counter.builder("ctrpc.rpc.calls")
                .description("Total CTRPC calls")
                .tag("side", side)
                .tag("service", service)
                .tag("method", method)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    private void record(Timer.Sample sample, String side, String service, String method, boolean success) {
        String outcome = success ? "success" : "failure";
        sample.stop(Timer.builder("ctrpc.rpc.latency")
                .description("CTRPC call latency")
                .tag("side", side)
                .tag("service", service)
                .tag("method", method)
                .tag("outcome", outcome)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry));
        increment(side, service, method, outcome);
    }
}
