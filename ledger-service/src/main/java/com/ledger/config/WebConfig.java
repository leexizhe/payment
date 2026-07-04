package com.ledger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ChaosInterceptor chaosInterceptor;

    public WebConfig(ChaosInterceptor chaosInterceptor) {
        this.chaosInterceptor = chaosInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Chaos only hits the payment path — actuator, accounts, and the chaos
        // API itself stay healthy so you can always observe and recover.
        registry.addInterceptor(chaosInterceptor).addPathPatterns("/api/payments/**");
    }
}
