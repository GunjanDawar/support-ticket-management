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
class TicketSearchRepositoryTests {

    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final EntityManager entityManager;

    @Autowired
    TicketSearchRepositoryTests(
            TicketRepository ticketRepository,
            CommentRepository commentRepository,
            EntityManager entityManager) {
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.entityManager = entityManager;
    }

    @Test
    void keywordSearchMatchesPartialTitleOrDescriptionIgnoringCase() {
        saveTicket("Payment Failure", "Card issue", TicketStatus.OPEN);
        saveTicket("Login problem", "PAYMENT gateway unavailable", TicketStatus.RESOLVED);
        saveTicket("Printer issue", "Hardware failure", TicketStatus.OPEN);
        flushAndClear();

        assertThat(ticketRepository.searchByKeyword("pay"))
                .extracting(Ticket::getTitle)
                .containsExactlyInAnyOrder("Payment Failure", "Login problem");
    }

    @Test
    void statusFilterMatchesEachApprovedStatusExactly() {
        for (TicketStatus status : TicketStatus.values()) {
            saveTicket(status + " ticket", "Status fixture", status);
        }
        flushAndClear();

        for (TicketStatus status : TicketStatus.values()) {
            assertThat(ticketRepository.findAllByStatus(status))
                    .extracting(Ticket::getStatus)
                    .containsExactly(status);
        }
    }

    @Test
    void combinedSearchAppliesKeywordGroupAndExactStatus() {
        saveTicket("Payment failed", "Card issue", TicketStatus.OPEN);
        saveTicket("Payment retry", "Gateway issue", TicketStatus.RESOLVED);
        saveTicket("Login problem", "Payment gateway unrelated", TicketStatus.OPEN);
        saveTicket("Printer issue", "Hardware failure", TicketStatus.OPEN);
        flushAndClear();

        assertThat(ticketRepository.searchByKeywordAndStatus("payment", TicketStatus.OPEN))
                .extracting(Ticket::getTitle)
                .containsExactlyInAnyOrder("Payment failed", "Login problem");
    }

    @Test
    void emptyKeywordDoesNotRestrictKeywordOrCombinedQueries() {
        saveTicket("Open ticket", "First", TicketStatus.OPEN);
        saveTicket("Closed ticket", "Second", TicketStatus.CLOSED);
        flushAndClear();

        assertThat(ticketRepository.searchByKeyword(""))
                .extracting(Ticket::getTitle)
                .containsExactlyInAnyOrder("Open ticket", "Closed ticket");
        assertThat(ticketRepository.searchByKeywordAndStatus("", TicketStatus.OPEN))
                .extracting(Ticket::getTitle)
                .containsExactly("Open ticket");
    }

    @Test
    void keywordSearchReturnsEmptyCollectionWhenNothingMatches() {
        saveTicket("Printer issue", "Hardware failure", TicketStatus.OPEN);
        flushAndClear();

        assertThat(ticketRepository.searchByKeyword("payment")).isEmpty();
    }

    @Test
    void keywordSearchDoesNotInspectUnsupportedFieldsOrComments() {
        Ticket ticket = new Ticket(
                "Printer issue",
                "Hardware failure",
                Priority.CRITICAL,
                "payment-team");
        ticket.setStatus(TicketStatus.OPEN);
        ticketRepository.save(ticket);
        commentRepository.save(new Comment(
                ticket,
                "payment appears only in a comment",
                Instant.parse("2026-09-22T17:00:00Z")));
        flushAndClear();

        assertThat(ticketRepository.searchByKeyword("payment")).isEmpty();
        assertThat(ticketRepository.searchByKeyword("critical")).isEmpty();
        assertThat(ticketRepository.searchByKeyword("open")).isEmpty();
        assertThat(ticketRepository.searchByKeyword(ticket.getId().toString())).isEmpty();
        assertThat(ticketRepository.searchByKeyword("2026-09-22")).isEmpty();
    }

    @Test
    void likeWildcardCharactersAreTreatedAsLiteralKeywordContent() {
        saveTicket("One hundred percent", "No symbols", TicketStatus.OPEN);
        saveTicket("Literal % marker", "Literal _ marker", TicketStatus.OPEN);
        flushAndClear();

        assertThat(ticketRepository.searchByKeyword("%"))
                .extracting(Ticket::getTitle)
                .containsExactly("Literal % marker");
        assertThat(ticketRepository.searchByKeyword("_"))
                .extracting(Ticket::getTitle)
                .containsExactly("Literal % marker");
    }

    private Ticket saveTicket(String title, String description, TicketStatus status) {
        Ticket ticket = new Ticket(title, description, Priority.MEDIUM, null);
        ticket.setStatus(status);
        return ticketRepository.save(ticket);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
