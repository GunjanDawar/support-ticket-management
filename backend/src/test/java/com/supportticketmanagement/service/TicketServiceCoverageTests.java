package com.supportticketmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.supportticketmanagement.dto.AddCommentRequest;
import com.supportticketmanagement.dto.CommentResponse;
import com.supportticketmanagement.dto.TicketDetailResponse;
import com.supportticketmanagement.dto.TicketSummaryResponse;
import com.supportticketmanagement.dto.UpdateTicketRequest;
import com.supportticketmanagement.persistence.Comment;
import com.supportticketmanagement.persistence.Priority;
import com.supportticketmanagement.persistence.Ticket;
import com.supportticketmanagement.persistence.TicketStatus;
import com.supportticketmanagement.repository.CommentRepository;
import com.supportticketmanagement.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TicketServiceCoverageTests {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private CommentRepository commentRepository;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, commentRepository);
    }

    @Test
    void listMapsEverySummaryFieldIncludingNullableAssignee() {
        Ticket assigned = ticket(1L, TicketStatus.OPEN, "Support Team");
        Ticket unassigned = ticket(2L, TicketStatus.RESOLVED, null);
        when(ticketRepository.findAll()).thenReturn(List.of(assigned, unassigned));

        List<TicketSummaryResponse> responses = ticketService.listTickets(null, null);

        TicketSummaryResponse first = responses.stream()
                .filter(response -> response.getId().equals("1"))
                .findFirst()
                .orElseThrow();
        TicketSummaryResponse second = responses.stream()
                .filter(response -> response.getId().equals("2"))
                .findFirst()
                .orElseThrow();
        assertThat(first.getTitle()).isEqualTo("Original title");
        assertThat(first.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(first.getAssignee()).isEqualTo("Support Team");
        assertThat(first.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(second.getAssignee()).isNull();
        assertThat(second.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void detailMapsAllTicketAndCommentFields() {
        Ticket ticket = ticket(42L, TicketStatus.IN_PROGRESS, "Support Team");
        Instant timestamp = Instant.parse("2026-09-22T10:15:30Z");
        Comment comment = new Comment(ticket, "Comment body", timestamp);
        ReflectionTestUtils.setField(comment, "id", 84L);
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findAllByTicket(ticket)).thenReturn(List.of(comment));

        TicketDetailResponse response = ticketService.getTicketDetail(42L);

        assertThat(response.getId()).isEqualTo("42");
        assertThat(response.getTitle()).isEqualTo("Original title");
        assertThat(response.getDescription()).isEqualTo("Original description");
        assertThat(response.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(response.getAssignee()).isEqualTo("Support Team");
        assertThat(response.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(response.getComments()).singleElement().satisfies(mapped -> {
            assertThat(mapped.getId()).isEqualTo("84");
            assertThat(mapped.getBody()).isEqualTo("Comment body");
            assertThat(mapped.getTimestamp()).isEqualTo(timestamp);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"description", "priority", "assignee"})
    void ordinaryUpdateSupportsEachPreviouslyUngroupedSingleField(String field) {
        Ticket ticket = ticket(42L, TicketStatus.IN_PROGRESS, "Original assignee");
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        UpdateTicketRequest request = new UpdateTicketRequest();
        switch (field) {
            case "description" -> request.setDescription("Updated description");
            case "priority" -> request.setPriority(Priority.CRITICAL);
            case "assignee" -> request.setAssignee("Updated assignee");
            default -> throw new IllegalArgumentException("Unknown field: " + field);
        }

        ticketService.updateTicket(42L, request);

        assertThat(ticket.getTitle()).isEqualTo("Original title");
        assertThat(ticket.getDescription()).isEqualTo(
                field.equals("description") ? "Updated description" : "Original description");
        assertThat(ticket.getPriority()).isEqualTo(
                field.equals("priority") ? Priority.CRITICAL : Priority.HIGH);
        assertThat(ticket.getAssignee()).isEqualTo(
                field.equals("assignee") ? "Updated assignee" : "Original assignee");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void ordinaryMultiFieldUpdatePreservesOmittedFields() {
        Ticket ticket = ticket(42L, TicketStatus.RESOLVED, "Original assignee");
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Updated title");
        request.setPriority(Priority.LOW);

        ticketService.updateTicket(42L, request);

        assertThat(ticket.getTitle()).isEqualTo("Updated title");
        assertThat(ticket.getPriority()).isEqualTo(Priority.LOW);
        assertThat(ticket.getDescription()).isEqualTo("Original description");
        assertThat(ticket.getAssignee()).isEqualTo("Original assignee");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void commentCreationPreservesWhitespaceBodyAndMapsGeneratedValues() {
        Ticket ticket = ticket(42L, TicketStatus.OPEN, null);
        String body = "  Investigated by support.  ";
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 84L);
            return saved;
        });

        CommentResponse response =
                ticketService.addComment(42L, new AddCommentRequest(body));

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        org.mockito.Mockito.verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getBody()).isEqualTo(body);
        assertThat(captor.getValue().getTimestamp()).isNotNull();
        assertThat(response.getId()).isEqualTo("84");
        assertThat(response.getBody()).isEqualTo(body);
        assertThat(response.getTimestamp()).isEqualTo(captor.getValue().getTimestamp());
    }

    @Test
    void listAndDetailUseReadOnlyTransactions() throws Exception {
        Method listMethod = TicketService.class.getMethod(
                "listTickets", String.class, TicketStatus.class);
        Method detailMethod = TicketService.class.getMethod("getTicketDetail", Long.class);
        Transactional listTransaction =
                AnnotatedElementUtils.findMergedAnnotation(listMethod, Transactional.class);
        Transactional detailTransaction =
                AnnotatedElementUtils.findMergedAnnotation(detailMethod, Transactional.class);

        assertThat(listTransaction).isNotNull();
        assertThat(listTransaction.readOnly()).isTrue();
        assertThat(detailTransaction).isNotNull();
        assertThat(detailTransaction.readOnly()).isTrue();
    }

    private Ticket ticket(Long id, TicketStatus status, String assignee) {
        Ticket ticket = new Ticket(
                "Original title",
                "Original description",
                Priority.HIGH,
                assignee);
        ticket.setStatus(status);
        ReflectionTestUtils.setField(ticket, "id", id);
        return ticket;
    }
}
