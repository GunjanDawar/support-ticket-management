package com.supportticketmanagement.dto;

import com.supportticketmanagement.persistence.Priority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotNull(message = "title is required")
        @Size(max = 200, message = "title must not exceed 200 characters")
        String title,

        @NotNull(message = "description is required")
        @Size(max = 5_000, message = "description must not exceed 5000 characters")
        String description,

        @NotNull(message = "priority is required")
        Priority priority,

        String assignee) {
}
