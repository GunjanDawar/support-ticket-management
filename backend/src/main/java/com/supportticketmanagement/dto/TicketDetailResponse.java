package com.supportticketmanagement.dto;

import java.util.List;
import java.util.Objects;

import com.supportticketmanagement.persistence.Priority;
import com.supportticketmanagement.persistence.TicketStatus;

public final class TicketDetailResponse {

    private final String id;
    private final String title;
    private final String description;
    private final Priority priority;
    private final String assignee;
    private final TicketStatus status;
    private final List<CommentResponse> comments;

    public TicketDetailResponse(
            Long id,
            String title,
            String description,
            Priority priority,
            String assignee,
            TicketStatus status,
            List<CommentResponse> comments) {
        this.id = Objects.requireNonNull(id, "id must not be null").toString();
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.assignee = assignee;
        this.status = status;
        this.comments = List.copyOf(comments);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
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

    public List<CommentResponse> getComments() {
        return comments;
    }
}
