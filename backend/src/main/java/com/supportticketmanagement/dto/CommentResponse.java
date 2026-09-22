package com.supportticketmanagement.dto;

import java.time.Instant;
import java.util.Objects;

public final class CommentResponse {

    private final String id;
    private final String body;
    private final Instant timestamp;

    public CommentResponse(Long id, String body, Instant timestamp) {
        this.id = Objects.requireNonNull(id, "id must not be null").toString();
        this.body = body;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public String getBody() {
        return body;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
