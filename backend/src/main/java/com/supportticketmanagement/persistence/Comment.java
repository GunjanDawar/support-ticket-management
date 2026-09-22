package com.supportticketmanagement.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "comments",
        indexes = @Index(name = "idx_comments_ticket_id", columnList = "ticket_id"))
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "ticket_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_comments_ticket"))
    private Ticket ticket;

    @Lob
    @Column(nullable = false, updatable = false)
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    protected Comment() {
    }

    public Comment(Ticket ticket, String body, Instant timestamp) {
        this.ticket = ticket;
        this.body = body;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public String getBody() {
        return body;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
