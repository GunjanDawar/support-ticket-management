package com.supportticketmanagement.dto;

import java.util.Objects;

import com.supportticketmanagement.persistence.Priority;
import com.supportticketmanagement.persistence.TicketStatus;

public final class TicketSummaryResponse {

    private final String id;
    private final String title;
    private final Priority priority;
    private final String assignee;
    private final TicketStatus status;

    public TicketSummaryResponse(
            Long id,
            String title,
            Priority priority,
            String assignee,
            TicketStatus status) {
        this.id = Objects.requireNonNull(id, "id must not be null").toString();
        this.title = title;
        this.priority = priority;
        this.assignee = assignee;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Priority getPriority() {
        return priority;
    }

    public String getAssignee() {
        return assignee;
    }

    public TicketStatus getStatus() {
        return status;
    }
}
