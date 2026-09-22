package com.supportticketmanagement.dto;

import com.supportticketmanagement.persistence.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record StatusTransitionRequest(
        @NotNull(message = "targetStatus is required")
        TicketStatus targetStatus) {
}
