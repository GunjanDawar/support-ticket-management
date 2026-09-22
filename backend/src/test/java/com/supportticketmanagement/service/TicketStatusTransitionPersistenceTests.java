package com.supportticketmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supportticketmanagement.dto.StatusTransitionRequest;
import com.supportticketmanagement.error.InvalidStatusTransitionException;
import com.supportticketmanagement.persistence.Priority;
import com.supportticketmanagement.persistence.Ticket;
import com.supportticketmanagement.persistence.TicketStatus;
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
class TicketStatusTransitionPersistenceTests {

    private final TicketService ticketService;
    private final TicketRepository ticketRepository;
    private final EntityManager entityManager;

    @Autowired
    TicketStatusTransitionPersistenceTests(
            TicketService ticketService,
            TicketRepository ticketRepository,
            EntityManager entityManager) {
        this.ticketService = ticketService;
        this.ticketRepository = ticketRepository;
        this.entityManager = entityManager;
    }

    @Test
    void validTransitionIsPersisted() {
        Ticket ticket = persistOpenTicket();

        ticketService.changeStatus(
                ticket.getId(),
                new StatusTransitionRequest(TicketStatus.IN_PROGRESS));
        entityManager.flush();
        entityManager.clear();

        Ticket persisted = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertUnchangedNonStatusFields(persisted);
    }

    @Test
    void invalidTransitionLeavesPersistedTicketUnchanged() {
        Ticket ticket = persistOpenTicket();

        assertThatThrownBy(() -> ticketService.changeStatus(
                ticket.getId(),
                new StatusTransitionRequest(TicketStatus.CLOSED)))
                .isInstanceOf(InvalidStatusTransitionException.class);
        entityManager.clear();

        Ticket persisted = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertUnchangedNonStatusFields(persisted);
    }

    private Ticket persistOpenTicket() {
        Ticket ticket = new Ticket(
                "Original title",
                "Original description",
                Priority.HIGH,
                "Original assignee");
        ticket.setStatus(TicketStatus.OPEN);
        ticketRepository.save(ticket);
        entityManager.flush();
        entityManager.clear();
        return ticket;
    }

    private void assertUnchangedNonStatusFields(Ticket ticket) {
        assertThat(ticket.getTitle()).isEqualTo("Original title");
        assertThat(ticket.getDescription()).isEqualTo("Original description");
        assertThat(ticket.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(ticket.getAssignee()).isEqualTo("Original assignee");
    }
}
