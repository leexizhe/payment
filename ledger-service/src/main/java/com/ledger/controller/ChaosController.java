package com.ledger.controller;

import com.ledger.dto.chaos.request.ChaosRequest;
import com.ledger.dto.chaos.response.ChaosStatusResponse;
import com.ledger.service.chaos.ChaosService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Incident switchboard. POST {"latencyMs": 800, "errorRate": 0.3} to start a
 * synthetic outage on /api/payments; POST zeros to end it.
 */
@RestController
@RequestMapping("/api/chaos")
public class ChaosController {

    private final ChaosService chaos;

    public ChaosController(ChaosService chaos) {
        this.chaos = chaos;
    }

    @PostMapping
    public ChaosStatusResponse configure(@RequestBody @Valid ChaosRequest request) {
        chaos.configure(request.latencyMs(), request.errorRate());
        return status();
    }

    @GetMapping
    public ChaosStatusResponse status() {
        return new ChaosStatusResponse(chaos.isActive(), chaos.getLatencyMs(), chaos.getErrorRate());
    }
}
