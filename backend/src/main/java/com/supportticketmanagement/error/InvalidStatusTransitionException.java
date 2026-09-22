package com.supportticketmanagement.error;

import com.supportticketmanagement.persistence.TicketStatus;

public final class InvalidStatusTransitionException extends RuntimeException {

    private final TicketStatus currentStatus;
    private final TicketStatus targetStatus;

    public InvalidStatusTransitionException(
            TicketStatus currentStatus,
            TicketStatus targetStatus) {
        super("Transition from " + currentStatus + " to " + targetStatus + " is not allowed.");
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public TicketStatus getCurrentStatus() {
        return currentStatus;
    }

    public TicketStatus getTargetStatus() {
        return targetStatus;
    }
}
