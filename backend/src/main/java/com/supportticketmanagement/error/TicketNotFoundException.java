package com.supportticketmanagement.error;

public final class TicketNotFoundException extends RuntimeException {

    private final Long ticketId;

    public TicketNotFoundException(Long ticketId) {
        super("Ticket " + ticketId + " was not found.");
        this.ticketId = ticketId;
    }

    public Long getTicketId() {
        return ticketId;
    }
}
