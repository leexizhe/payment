package com.ledger.exception;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain failures to proper status codes so the http_server_requests
 * RED metrics distinguish client errors (4xx) from real outages (5xx).
 * Bodies use RFC-9457 ProblemDetail, matching what Spring MVC emits for
 * validation errors and ResponseStatusException (problemdetails enabled).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail accountNotFound(AccountNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail insufficientFunds(InsufficientFundsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(ChaosException.class)
    public ProblemDetail chaos(ChaosException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail lockConflict(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Concurrent update conflict — retry the request");
    }
}
