package com.converter.common.health;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class ReadinessCheckExecutor {

    private static final ExecutorService PROBE_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "readiness-probe");
        t.setDaemon(true);
        return t;
    });

    private ReadinessCheckExecutor() {}

    public static String resolveHostname() {
        String envHost = System.getenv("HOSTNAME");
        if (envHost != null && !envHost.isBlank()) {
            return envHost;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "unknown";
        }
    }

    public static HealthResponseDto healthy(String serviceName, Clock clock) {
        return new HealthResponseDto("ok", serviceName, resolveHostname(), clock.instant());
    }

    public static HealthResponseDto.ReadinessResponseDto evaluate(
            String serviceName,
            Clock clock,
            Map<String, Supplier<Boolean>> checks,
            Duration perCheckTimeout
    ) {
        Map<String, String> results = new LinkedHashMap<>();
        boolean allOk = true;
        for (Map.Entry<String, Supplier<Boolean>> entry : checks.entrySet()) {
            boolean ok = runBounded(() -> Boolean.TRUE.equals(entry.getValue().get()), perCheckTimeout);
            results.put(entry.getKey(), ok ? "ok" : "error");
            if (!ok) {
                allOk = false;
            }
        }
        return new HealthResponseDto.ReadinessResponseDto(
                allOk ? "ready" : "not_ready",
                serviceName,
                resolveHostname(),
                results,
                clock.instant()
        );
    }

    public static boolean runBounded(Callable<Boolean> task, Duration timeout) {
        Future<Boolean> future = PROBE_EXECUTOR.submit(task);
        try {
            return Boolean.TRUE.equals(future.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (Exception ex) {
            future.cancel(true);
            return false;
        }
    }
}
