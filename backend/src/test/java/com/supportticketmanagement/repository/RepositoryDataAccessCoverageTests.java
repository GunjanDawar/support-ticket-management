package com.supportticketmanagement.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import com.supportticketmanagement.persistence.Comment;
import com.supportticketmanagement.persistence.Priority;
import com.supportticketmanagement.persistence.Ticket;
import com.supportticketmanagement.persistence.TicketStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryDataAccessCoverageTests {

    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final EntityManager entityManager;

    @Autowired
    RepositoryDataAccessCoverageTests(
            TicketRepository ticketRepository,
            CommentRepository commentRepository,
            EntityManager entityManager) {
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.entityManager = entityManager;
    }

    @Test
    void ticketGeneratedIdFieldsNullableAssigneeAndTextEnumsRoundTrip() {
        Ticket assigned = new Ticket(
                "Assigned ticket",
                "Assigned description",
                Priority.CRITICAL,
                "Support Team");
        assigned.setStatus(TicketStatus.IN_PROGRESS);
        Ticket unassigned = new Ticket(
                "Unassigned ticket",
                "Unassigned description",
                Priority.LOW,
                null);

        ticketRepository.saveAll(List.of(assigned, unassigned));
        entityManager.flush();
        entityManager.clear();

        assertThat(assigned.getId()).isNotNull();
        assertThat(unassigned.getId()).isNotNull();
        Ticket persistedAssigned =
                ticketRepository.findById(assigned.getId()).orElseThrow();
        Ticket persistedUnassigned =
                ticketRepository.findById(unassigned.getId()).orElseThrow();
        assertThat(persistedAssigned.getTitle()).isEqualTo("Assigned ticket");
        assertThat(persistedAssigned.getDescription()).isEqualTo("Assigned description");
        assertThat(persistedAssigned.getPriority()).isEqualTo(Priority.CRITICAL);
        assertThat(persistedAssigned.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(persistedAssigned.getAssignee()).isEqualTo("Support Team");
        assertThat(persistedUnassigned.getAssignee()).isNull();

        Object[] storedEnums = (Object[]) entityManager
                .createNativeQuery("select priority, status from tickets where id = :id")
                .setParameter("id", assigned.getId())
                .getSingleResult();
        assertThat(storedEnums).containsExactly("CRITICAL", "IN_PROGRESS");
    }

    @Test
    void commentGeneratedIdRelationshipBodyAndTimestampRoundTripExactly() {
        Ticket ticket = ticketRepository.save(ticket("Comment owner", TicketStatus.OPEN));
        Instant timestamp = Instant.parse("2026-09-22T10:15:30Z");
        Comment comment = new Comment(
                ticket,
                "  Investigated by support team.  ",
                timestamp);

        commentRepository.save(comment);
        entityManager.flush();
        entityManager.clear();

        assertThat(comment.getId()).isNotNull();
        Comment persisted = commentRepository.findById(comment.getId()).orElseThrow();
        assertThat(persisted.getTicket().getId()).isEqualTo(ticket.getId());
        assertThat(persisted.getBody()).isEqualTo("  Investigated by support team.  ");
        assertThat(persisted.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    void commentLookupByTicketHandlesZeroOneMultipleAndExcludesOtherTickets() {
        Ticket firstTicket = ticketRepository.save(ticket("First ticket", TicketStatus.OPEN));
        Ticket secondTicket = ticketRepository.save(ticket("Second ticket", TicketStatus.OPEN));

        assertThat(commentRepository.findAllByTicket(firstTicket)).isEmpty();

        Comment first = commentRepository.save(new Comment(
                firstTicket, "First comment", Instant.parse("2026-09-22T10:15:30Z")));
        entityManager.flush();
        assertThat(commentRepository.findAllByTicket(firstTicket))
                .extracting(Comment::getId)
                .containsExactly(first.getId());

        Comment second = commentRepository.save(new Comment(
                firstTicket, "Second comment", Instant.parse("2026-09-22T10:16:30Z")));
        commentRepository.save(new Comment(
                secondTicket, "Other ticket", Instant.parse("2026-09-22T10:17:30Z")));
        entityManager.flush();
        entityManager.clear();

        assertThat(commentRepository.findAllByTicket(firstTicket))
                .extracting(Comment::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    void exactStatusQueryCoversEveryStatusAndEmptyResults() {
        for (TicketStatus status : TicketStatus.values()) {
            ticketRepository.save(ticket(status + " ticket", status));
        }
        entityManager.flush();

        for (TicketStatus status : TicketStatus.values()) {
            assertThat(ticketRepository.findAllByStatus(status))
                    .extracting(Ticket::getStatus)
                    .containsExactly(status);
        }

        ticketRepository.deleteAll();
        entityManager.flush();
        assertThat(ticketRepository.findAllByStatus(TicketStatus.OPEN)).isEmpty();
    }

    @Test
    void keywordSearchIsPartialCaseInsensitiveTitleOrDescriptionOnly() {
        Ticket titleMatch = ticketRepository.save(
                ticket("Printer Failure", TicketStatus.OPEN));
        Ticket descriptionMatch = new Ticket(
                "Laptop issue",
                "A PRINTER is mentioned in the description",
                Priority.MEDIUM,
                null);
        descriptionMatch.setStatus(TicketStatus.RESOLVED);
        ticketRepository.save(descriptionMatch);
        Ticket unsupportedFieldOnly = new Ticket(
                "Network issue",
                "Connectivity problem",
                Priority.CRITICAL,
                "printer-team");
        unsupportedFieldOnly.setStatus(TicketStatus.CLOSED);
        ticketRepository.save(unsupportedFieldOnly);
        entityManager.flush();

        for (String keyword : List.of("printer", "PRINTER", "PrInTeR", "print")) {
            assertThat(ticketRepository.searchByKeyword(keyword))
                    .extracting(Ticket::getId)
                    .containsExactlyInAnyOrder(titleMatch.getId(), descriptionMatch.getId());
        }
        assertThat(ticketRepository.searchByKeyword("absent")).isEmpty();
        assertThat(ticketRepository.searchByKeyword("printer-team"))
                .doesNotContain(unsupportedFieldOnly);
    }

    @Test
    void combinedKeywordAndStatusUsesGroupedAndSemantics() {
        Ticket openTitle = ticketRepository.save(
                ticket("Printer issue", TicketStatus.OPEN));
        ticketRepository.save(ticket("Printer issue", TicketStatus.CLOSED));
        Ticket openDescription = new Ticket(
                "Laptop issue",
                "Printer mentioned",
                Priority.MEDIUM,
                null);
        openDescription.setStatus(TicketStatus.OPEN);
        ticketRepository.save(openDescription);
        ticketRepository.save(ticket("Printer issue", TicketStatus.IN_PROGRESS));
        ticketRepository.save(ticket("Unrelated issue", TicketStatus.OPEN));
        entityManager.flush();

        assertThat(ticketRepository.searchByKeywordAndStatus("printer", TicketStatus.OPEN))
                .extracting(Ticket::getId)
                .containsExactlyInAnyOrder(openTitle.getId(), openDescription.getId());
    }

    @Test
    void emptyKeywordIsUnrestrictedAndLikeMetacharactersAreLiteral() {
        Ticket plain = ticketRepository.save(
                ticket("Plain ticket", TicketStatus.OPEN));
        Ticket symbols = ticketRepository.save(
                ticket("Literal % marker", TicketStatus.CLOSED));
        symbols.setDescription("Literal _ marker");
        entityManager.flush();

        assertThat(ticketRepository.searchByKeyword(""))
                .extracting(Ticket::getId)
                .containsExactlyInAnyOrder(plain.getId(), symbols.getId());
        assertThat(ticketRepository.searchByKeyword("%"))
                .extracting(Ticket::getId)
                .containsExactly(symbols.getId());
        assertThat(ticketRepository.searchByKeyword("_"))
                .extracting(Ticket::getId)
                .containsExactly(symbols.getId());
        assertThat(ticketRepository.searchByKeywordAndStatus("", TicketStatus.OPEN))
                .extracting(Ticket::getId)
                .containsExactly(plain.getId());
    }

    @ParameterizedTest(name = "database rejects missing {0}")
    @ValueSource(strings = {
        "ticket.title",
        "ticket.description",
        "ticket.priority",
        "ticket.status",
        "comment.ticket",
        "comment.body",
        "comment.timestamp"
    })
    void databaseRejectsRequiredNullPersistenceFields(String missingField) {
        assertThatThrownBy(() -> persistWithMissingRequiredField(missingField))
                .isInstanceOf(PersistenceException.class);
    }

    @ParameterizedTest(name = "database rejects oversized {0}")
    @MethodSource("oversizedTicketFields")
    void databaseRejectsValuesBeyondMappedTicketLengths(
            String field,
            String value) {
        Ticket ticket = field.equals("title")
                ? new Ticket(value, "Description", Priority.LOW, null)
                : new Ticket("Title", value, Priority.LOW, null);

        assertThatThrownBy(() -> {
            entityManager.persist(ticket);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    private void persistWithMissingRequiredField(String missingField) {
        switch (missingField) {
            case "ticket.title" ->
                    persistAndFlush(new Ticket(null, "Description", Priority.LOW, null));
            case "ticket.description" ->
                    persistAndFlush(new Ticket("Title", null, Priority.LOW, null));
            case "ticket.priority" ->
                    persistAndFlush(new Ticket("Title", "Description", null, null));
            case "ticket.status" -> {
                Ticket ticket = ticket("Title", TicketStatus.OPEN);
                ticket.setStatus(null);
                persistAndFlush(ticket);
            }
            case "comment.ticket" ->
                    persistAndFlush(new Comment(
                            null, "Body", Instant.parse("2026-09-22T10:15:30Z")));
            case "comment.body" -> {
                Ticket ticket = ticketRepository.save(ticket("Owner", TicketStatus.OPEN));
                persistAndFlush(new Comment(
                        ticket, null, Instant.parse("2026-09-22T10:15:30Z")));
            }
            case "comment.timestamp" -> {
                Ticket ticket = ticketRepository.save(ticket("Owner", TicketStatus.OPEN));
                persistAndFlush(new Comment(ticket, "Body", null));
            }
            default -> throw new IllegalArgumentException("Unknown field: " + missingField);
        }
    }

    private void persistAndFlush(Object entity) {
        entityManager.persist(entity);
        entityManager.flush();
    }

    private Ticket ticket(String title, TicketStatus status) {
        Ticket ticket = new Ticket(title, "Description", Priority.MEDIUM, null);
        ticket.setStatus(status);
        return ticket;
    }

    private static Stream<Arguments> oversizedTicketFields() {
        return Stream.of(
                Arguments.of("title", "t".repeat(201)),
                Arguments.of("description", "d".repeat(5_001)));
    }
}
