package com.ledger.config;

import com.ledger.service.chaos.ChaosService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Runs after handler mapping (unlike a servlet filter), so the http_server_requests
 * metric keeps its proper uri tag when chaos turns a request into a 503.
 */
@Component
public class ChaosInterceptor implements HandlerInterceptor {

    private final ChaosService chaos;

    public ChaosInterceptor(ChaosService chaos) {
        this.chaos = chaos;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        chaos.maybeInject();
        return true;
    }
}
