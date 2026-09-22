package com.supportticketmanagement.error;

public record ApiErrorResponse(
        ApiErrorCode code,
        String message,
        int status,
        String path) {
}
