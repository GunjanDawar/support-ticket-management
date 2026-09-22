package com.supportticketmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supportticketmanagement.dto.AddCommentRequest;
import com.supportticketmanagement.dto.UpdateTicketRequest;
import com.supportticketmanagement.error.TerminalTicketConflictException;
import com.supportticketmanagement.persistence.Priority;
import com.supportticketmanagement.persistence.Ticket;
import com.supportticketmanagement.persistence.TicketStatus;
import com.supportticketmanagement.repository.CommentRepository;
import com.supportticketmanagement.repository.TicketRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TicketService.class)
class TicketServicePersistenceTests {

    private final TicketService ticketService;
    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final EntityManager entityManager;

    @Autowired
    TicketServicePersistenceTests(
            TicketService ticketService,
            TicketRepository ticketRepository,
            CommentRepository commentRepository,
            EntityManager entityManager) {
        this.ticketService = ticketService;
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.entityManager = entityManager;
    }

    @Test
    void rejectedTerminalUpdateLeavesPersistedTicketUnchanged() {
        Ticket ticket = new Ticket(
                "Original title",
                "Original description",
                Priority.HIGH,
                "Original assignee");
        ticket.setStatus(TicketStatus.CLOSED);
        ticketRepository.save(ticket);
        entityManager.flush();
        entityManager.clear();
        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Changed title");
        request.setDescription("Changed description");
        request.setPriority(Priority.LOW);
        request.setAssignee(null);

        assertThatThrownBy(() -> ticketService.updateTicket(ticket.getId(), request))
                .isInstanceOf(TerminalTicketConflictException.class);
        entityManager.clear();

        Ticket persisted = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("Original title");
        assertThat(persisted.getDescription()).isEqualTo("Original description");
        assertThat(persisted.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(persisted.getAssignee()).isEqualTo("Original assignee");
        assertThat(persisted.getStatus()).isEqualTo(TicketStatus.CLOSED);
    }

    @Test
    void rejectedTerminalCommentDoesNotPersistComment() {
        Ticket ticket = new Ticket(
                "Cancelled ticket",
                "Description",
                Priority.MEDIUM,
                null);
        ticket.setStatus(TicketStatus.CANCELLED);
        ticketRepository.save(ticket);
        entityManager.flush();
        entityManager.clear();
        long commentsBefore = commentRepository.count();

        assertThatThrownBy(() ->
                ticketService.addComment(ticket.getId(), new AddCommentRequest("Body")))
                .isInstanceOf(TerminalTicketConflictException.class);
        entityManager.flush();
        entityManager.clear();

        assertThat(commentRepository.count()).isEqualTo(commentsBefore);
    }
}
