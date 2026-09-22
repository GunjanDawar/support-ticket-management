package com.supportticketmanagement.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class,
        BindException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleValidationFailure(
            Exception exception,
            HttpServletRequest request) {
        return error(
                ApiErrorCode.VALIDATION_ERROR,
                HttpStatus.BAD_REQUEST,
                "The request contains invalid input.",
                request);
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTicketNotFound(
            TicketNotFoundException exception,
            HttpServletRequest request) {
        return error(
                ApiErrorCode.TICKET_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidStatusTransition(
            InvalidStatusTransitionException exception,
            HttpServletRequest request) {
        return error(
                ApiErrorCode.INVALID_STATUS_TRANSITION,
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(TerminalTicketConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleTerminalTicketConflict(
            TerminalTicketConflictException exception,
            HttpServletRequest request) {
        return error(
                ApiErrorCode.TERMINAL_TICKET_CONFLICT,
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request);
    }

    private ResponseEntity<ApiErrorResponse> error(
            ApiErrorCode code,
            HttpStatus status,
            String message,
            HttpServletRequest request) {
        ApiErrorResponse response = new ApiErrorResponse(
                code,
                message,
                status.value(),
                request.getRequestURI());
        return ResponseEntity.status(status).body(response);
    }
}
