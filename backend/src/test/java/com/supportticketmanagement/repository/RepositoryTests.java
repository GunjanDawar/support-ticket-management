package com.supportticketmanagement.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.supportticketmanagement.persistence.Comment;
import com.supportticketmanagement.persistence.Priority;
import com.supportticketmanagement.persistence.Ticket;
import com.supportticketmanagement.persistence.TicketStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryTests {

    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final EntityManager entityManager;

    @Autowired
    RepositoryTests(
            TicketRepository ticketRepository,
            CommentRepository commentRepository,
            EntityManager entityManager) {
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.entityManager = entityManager;
    }

    @Test
    void savesFindsChecksAndListsTickets() {
        Ticket saved = ticketRepository.save(
                new Ticket("Repository title", "Repository description", Priority.HIGH, null));
        entityManager.flush();
        entityManager.clear();

        Ticket retrieved = ticketRepository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getTitle()).isEqualTo("Repository title");
        assertThat(retrieved.getDescription()).isEqualTo("Repository description");
        assertThat(retrieved.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(retrieved.getAssignee()).isNull();
        assertThat(retrieved.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticketRepository.existsById(saved.getId())).isTrue();
        assertThat(ticketRepository.findAll())
                .extracting(Ticket::getId)
                .contains(saved.getId());
        assertThat(ticketRepository.findById(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void savesFindsAndRetrievesCommentsForTicketWithoutOrdering() {
        Ticket ticket = ticketRepository.save(
                new Ticket("Comment owner", "Description", Priority.MEDIUM, "Support Team"));

        Comment first = commentRepository.save(new Comment(
                ticket,
                "First body",
                Instant.parse("2026-09-22T17:00:00Z")));
        Comment second = commentRepository.save(new Comment(
                ticket,
                "Second body",
                Instant.parse("2026-09-22T17:01:00Z")));
        entityManager.flush();
        entityManager.clear();

        Ticket persistedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        Comment persistedComment = commentRepository.findById(first.getId()).orElseThrow();

        assertThat(persistedComment.getTicket().getId()).isEqualTo(persistedTicket.getId());
        assertThat(persistedComment.getTimestamp())
                .isEqualTo(Instant.parse("2026-09-22T17:00:00Z"));
        assertThat(commentRepository.existsById(second.getId())).isTrue();
        assertThat(commentRepository.findAllByTicket(persistedTicket))
                .extracting(Comment::getBody)
                .containsExactlyInAnyOrder("First body", "Second body");
        assertThat(commentRepository.findById(Long.MAX_VALUE)).isEmpty();
    }
}
