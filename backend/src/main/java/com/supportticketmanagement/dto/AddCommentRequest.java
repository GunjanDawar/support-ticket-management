package com.supportticketmanagement.dto;

import jakarta.validation.constraints.NotNull;

public record AddCommentRequest(
        @NotNull(message = "body is required")
        String body) {
}
