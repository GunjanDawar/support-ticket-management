package com.supportticketmanagement.error;

import com.supportticketmanagement.persistence.TicketStatus;

public final class TerminalTicketConflictException extends RuntimeException {

    private final Long ticketId;
    private final TicketStatus status;

    public TerminalTicketConflictException(Long ticketId, TicketStatus status) {
        super("Ticket " + ticketId + " in status " + status
                + " does not allow the requested operation.");
        this.ticketId = ticketId;
        this.status = status;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public TicketStatus getStatus() {
        return status;
    }
}
