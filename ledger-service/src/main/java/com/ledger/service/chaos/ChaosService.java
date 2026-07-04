package com.ledger.service.chaos;

import com.ledger.exception.ChaosException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Runtime failure injection for SRE practice: add latency and/or a 5xx error
 * rate to the payments endpoint, then watch the RED dashboards degrade and
 * recover. Zero means off. Bounds are enforced by validation on ChaosRequest.
 */
@Service
public class ChaosService {

    // volatile is enough: each field is read/written whole, never compound-updated.
    private volatile long latencyMs = 0;
    private volatile double errorRate = 0;

    public void configure(long latencyMs, double errorRate) {
        this.latencyMs = latencyMs;
        this.errorRate = errorRate;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public boolean isActive() {
        return latencyMs > 0 || errorRate > 0;
    }

    /** Called per request by the interceptor. */
    public void maybeInject() {
        long delay = latencyMs;
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < errorRate) {
            throw new ChaosException();
        }
    }
}
