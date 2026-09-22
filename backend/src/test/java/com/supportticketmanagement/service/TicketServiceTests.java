package com.supportticketmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.supportticketmanagement.dto.AddCommentRequest;
import com.supportticketmanagement.dto.CommentResponse;
import com.supportticketmanagement.dto.CreateTicketRequest;
import com.supportticketmanagement.dto.TicketDetailResponse;
import com.supportticketmanagement.dto.TicketResponse;
import com.supportticketmanagement.dto.TicketSummaryResponse;
import com.supportticketmanagement.dto.UpdateTicketRequest;
import com.supportticketmanagement.error.TerminalTicketConflictException;
import com.supportticketmanagement.error.TicketNotFoundException;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TicketServiceTests {

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
    void createCopiesApprovedFieldsForcesOpenAndReturnsTicketResponse() {
        CreateTicketRequest request = new CreateTicketRequest(
                "Title",
                "Description",
                Priority.HIGH,
                "Support Team");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });

        TicketResponse response = ticketService.createTicket(request);

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        Ticket persisted = ticketCaptor.getValue();
        assertThat(persisted.getTitle()).isEqualTo("Title");
        assertThat(persisted.getDescription()).isEqualTo("Description");
        assertThat(persisted.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(persisted.getAssignee()).isEqualTo("Support Team");
        assertThat(persisted.getStatus()).isEqualTo(TicketStatus.OPEN);

        assertThat(response).isInstanceOf(TicketResponse.class);
        assertThat(response.getId()).isEqualTo("42");
        assertThat(response.getTitle()).isEqualTo("Title");
        assertThat(response.getDescription()).isEqualTo("Description");
        assertThat(response.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(response.getAssignee()).isEqualTo("Support Team");
        assertThat(response.getStatus()).isEqualTo(TicketStatus.OPEN);
        verifyNoInteractions(commentRepository);
    }

    @Test
    void createPreservesNullAssigneeAndOwnsWriteTransaction() throws Exception {
        CreateTicketRequest request =
                new CreateTicketRequest("Title", "Description", Priority.LOW, null);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        TicketResponse response = ticketService.createTicket(request);
        Method method = TicketService.class.getMethod(
                "createTicket", CreateTicketRequest.class);
        Transactional transaction =
                AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

        assertThat(response.getAssignee()).isNull();
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isFalse();
    }

    @Test
    void listWithoutFiltersUsesBaseRepositoryAndSummaryResponses() {
        when(ticketRepository.findAll()).thenReturn(List.of(
                ticket(1L, "First", "First description", TicketStatus.OPEN),
                ticket(2L, "Second", "Second description", TicketStatus.CLOSED)));

        List<TicketSummaryResponse> responses = ticketService.listTickets(null, null);

        verify(ticketRepository).findAll();
        assertThat(responses)
                .extracting(TicketSummaryResponse::getId)
                .containsExactlyInAnyOrder("1", "2");
        assertThat(responses).allSatisfy(
                response -> assertThat(response).isInstanceOf(TicketSummaryResponse.class));
        verifyNoInteractions(commentRepository);
    }

    @Test
    void listWithKeywordDelegatesToKeywordRepositoryQuery() {
        when(ticketRepository.searchByKeyword("pay")).thenReturn(List.of(
                ticket(1L, "Payment failure", "Card issue", TicketStatus.OPEN)));

        List<TicketSummaryResponse> responses = ticketService.listTickets("pay", null);

        verify(ticketRepository).searchByKeyword("pay");
        assertThat(responses).extracting(TicketSummaryResponse::getTitle)
                .containsExactly("Payment failure");
        verifyNoInteractions(commentRepository);
    }

    @Test
    void listWithStatusDelegatesToExactStatusRepositoryQuery() {
        when(ticketRepository.findAllByStatus(TicketStatus.RESOLVED)).thenReturn(List.of(
                ticket(2L, "Resolved ticket", "Description", TicketStatus.RESOLVED)));

        List<TicketSummaryResponse> responses =
                ticketService.listTickets(null, TicketStatus.RESOLVED);

        verify(ticketRepository).findAllByStatus(TicketStatus.RESOLVED);
        assertThat(responses).extracting(TicketSummaryResponse::getStatus)
                .containsExactly(TicketStatus.RESOLVED);
    }

    @Test
    void listWithKeywordAndStatusDelegatesToCombinedAndRepositoryQuery() {
        when(ticketRepository.searchByKeywordAndStatus("payment", TicketStatus.OPEN))
                .thenReturn(List.of(
                        ticket(1L, "Payment failed", "Card issue", TicketStatus.OPEN),
                        ticket(3L, "Login issue", "Payment gateway", TicketStatus.OPEN)));

        List<TicketSummaryResponse> responses =
                ticketService.listTickets("payment", TicketStatus.OPEN);

        verify(ticketRepository).searchByKeywordAndStatus("payment", TicketStatus.OPEN);
        verify(ticketRepository, never()).searchByKeyword("payment");
        verify(ticketRepository, never()).findAllByStatus(TicketStatus.OPEN);
        assertThat(responses).extracting(TicketSummaryResponse::getId)
                .containsExactlyInAnyOrder("1", "3");
    }

    @Test
    void emptyKeywordBehavesAsNoKeywordRestriction() {
        when(ticketRepository.findAll()).thenReturn(List.of(
                ticket(1L, "First", "Description", TicketStatus.OPEN)));

        ticketService.listTickets("", null);

        verify(ticketRepository).findAll();
        verify(ticketRepository, never()).searchByKeyword("");

        clearInvocations(ticketRepository);
        when(ticketRepository.findAllByStatus(TicketStatus.OPEN)).thenReturn(List.of(
                ticket(1L, "First", "Description", TicketStatus.OPEN)));

        ticketService.listTickets("", TicketStatus.OPEN);

        verify(ticketRepository).findAllByStatus(TicketStatus.OPEN);
        verify(ticketRepository, never()).searchByKeywordAndStatus("", TicketStatus.OPEN);
    }

    @Test
    void listReturnsEmptyCollectionWhenRepositoryHasNoMatches() {
        when(ticketRepository.searchByKeyword("missing")).thenReturn(List.of());

        List<TicketSummaryResponse> responses =
                ticketService.listTickets("missing", null);

        assertThat(responses).isEmpty();
    }

    @Test
    void detailMapsTicketAndCommentsInRepositorySuppliedOrder() {
        Ticket ticket = ticket(
                42L, "Ticket", "Detailed description", TicketStatus.IN_PROGRESS);
        Comment second = comment(12L, ticket, "Second", "2026-09-22T10:16:00Z");
        Comment first = comment(11L, ticket, "First", "2026-09-22T10:15:00Z");
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findAllByTicket(ticket)).thenReturn(List.of(second, first));

        TicketDetailResponse response = ticketService.getTicketDetail(42L);

        verify(ticketRepository).findById(42L);
        verify(commentRepository).findAllByTicket(ticket);
        assertThat(response.getId()).isEqualTo("42");
        assertThat(response.getTitle()).isEqualTo("Ticket");
        assertThat(response.getDescription()).isEqualTo("Detailed description");
        assertThat(response.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(response.getComments())
                .extracting(commentResponse -> commentResponse.getId())
                .containsExactly("12", "11");
        assertThat(response.getComments())
                .extracting(commentResponse -> commentResponse.getBody())
                .containsExactly("Second", "First");
    }

    @Test
    void detailReturnsEmptyCommentsCollection() {
        Ticket ticket = ticket(42L, "Ticket", "Description", TicketStatus.OPEN);
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findAllByTicket(ticket)).thenReturn(List.of());

        TicketDetailResponse response = ticketService.getTicketDetail(42L);

        assertThat(response.getComments()).isEmpty();
    }

    @Test
    void detailThrowsTicketNotFoundWithoutLoadingComments() {
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getTicketDetail(999L))
                .isInstanceOf(TicketNotFoundException.class);
        verifyNoInteractions(commentRepository);
    }

    @Test
    void terminalTicketsRemainReadableThroughDetailAndFilter() {
        Ticket closed = ticket(10L, "Closed", "Description", TicketStatus.CLOSED);
        Ticket cancelled =
                ticket(11L, "Cancelled", "Description", TicketStatus.CANCELLED);
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(closed));
        when(commentRepository.findAllByTicket(closed)).thenReturn(List.of());
        when(ticketRepository.findAllByStatus(TicketStatus.CANCELLED))
                .thenReturn(List.of(cancelled));

        TicketDetailResponse detail = ticketService.getTicketDetail(10L);
        List<TicketSummaryResponse> filtered =
                ticketService.listTickets(null, TicketStatus.CANCELLED);

        assertThat(detail.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(filtered).extracting(TicketSummaryResponse::getStatus)
                .containsExactly(TicketStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(
            value = TicketStatus.class,
            names = {"OPEN", "IN_PROGRESS", "RESOLVED"})
    void ordinaryUpdateChangesApprovedFieldsAndPreservesNonTerminalStatus(
            TicketStatus status) {
        Ticket ticket = ticket(42L, "Old title", "Old description", status);
        ticket.setAssignee("Old assignee");
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("New title");
        request.setDescription("New description");
        request.setPriority(Priority.CRITICAL);
        request.setAssignee("New assignee");

        TicketResponse response = ticketService.updateTicket(42L, request);

        verify(ticketRepository).save(ticket);
        assertThat(ticket.getTitle()).isEqualTo("New title");
        assertThat(ticket.getDescription()).isEqualTo("New description");
        assertThat(ticket.getPriority()).isEqualTo(Priority.CRITICAL);
        assertThat(ticket.getAssignee()).isEqualTo("New assignee");
        assertThat(ticket.getStatus()).isEqualTo(status);
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getDescription()).isEqualTo("New description");
    }

    @Test
    void ordinaryUpdateLeavesOmittedFieldsUnchanged() {
        Ticket ticket = ticket(42L, "Old title", "Original description", TicketStatus.OPEN);
        ticket.setPriority(Priority.LOW);
        ticket.setAssignee("Original assignee");
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("New title");

        ticketService.updateTicket(42L, request);

        assertThat(ticket.getTitle()).isEqualTo("New title");
        assertThat(ticket.getDescription()).isEqualTo("Original description");
        assertThat(ticket.getPriority()).isEqualTo(Priority.LOW);
        assertThat(ticket.getAssignee()).isEqualTo("Original assignee");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void ordinaryUpdateExplicitNullAssigneeClearsAssignment() {
        Ticket ticket = ticket(42L, "Title", "Description", TicketStatus.IN_PROGRESS);
        ticket.setAssignee("Support Team");
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setAssignee(null);

        TicketResponse response = ticketService.updateTicket(42L, request);

        assertThat(ticket.getAssignee()).isNull();
        assertThat(response.getAssignee()).isNull();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(
            value = TicketStatus.class,
            names = {"CLOSED", "CANCELLED"})
    void terminalOrdinaryUpdateIsRejectedWithoutMutationOrSave(TicketStatus status) {
        Ticket ticket = ticket(42L, "Original title", "Original description", status);
        ticket.setPriority(Priority.HIGH);
        ticket.setAssignee("Original assignee");
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));
        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("Changed title");
        request.setDescription("Changed description");
        request.setPriority(Priority.LOW);
        request.setAssignee(null);

        assertThatThrownBy(() -> ticketService.updateTicket(42L, request))
                .isInstanceOf(TerminalTicketConflictException.class);

        verify(ticketRepository, never()).save(any(Ticket.class));
        assertThat(ticket.getTitle()).isEqualTo("Original title");
        assertThat(ticket.getDescription()).isEqualTo("Original description");
        assertThat(ticket.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(ticket.getAssignee()).isEqualTo("Original assignee");
        assertThat(ticket.getStatus()).isEqualTo(status);
    }

    @Test
    void missingTicketOrdinaryUpdateThrowsWithoutSave() {
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());
        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setTitle("New title");

        assertThatThrownBy(() -> ticketService.updateTicket(999L, request))
                .isInstanceOf(TicketNotFoundException.class);

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @ParameterizedTest
    @EnumSource(
            value = TicketStatus.class,
            names = {"OPEN", "IN_PROGRESS", "RESOLVED"})
    void commentCreationSucceedsForEveryNonTerminalStatus(TicketStatus status) {
        Ticket ticket = ticket(42L, "Ticket", "Description", status);
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 84L);
            return saved;
        });
        Instant before = Instant.now();

        CommentResponse response =
                ticketService.addComment(42L, new AddCommentRequest("Comment body"));
        Instant after = Instant.now();

        ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(commentCaptor.capture());
        Comment persisted = commentCaptor.getValue();
        assertThat(persisted.getTicket()).isSameAs(ticket);
        assertThat(persisted.getBody()).isEqualTo("Comment body");
        assertThat(persisted.getTimestamp()).isBetween(before, after);
        assertThat(response.getId()).isEqualTo("84");
        assertThat(response.getBody()).isEqualTo("Comment body");
        assertThat(response.getTimestamp()).isEqualTo(persisted.getTimestamp());
    }

    @ParameterizedTest
    @EnumSource(
            value = TicketStatus.class,
            names = {"CLOSED", "CANCELLED"})
    void terminalCommentCreationIsRejectedWithoutPersistence(TicketStatus status) {
        Ticket ticket = ticket(42L, "Ticket", "Description", status);
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() ->
                ticketService.addComment(42L, new AddCommentRequest("Comment body")))
                .isInstanceOf(TerminalTicketConflictException.class);

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void missingTicketCommentCreationThrowsWithoutPersistence() {
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                ticketService.addComment(999L, new AddCommentRequest("Comment body")))
                .isInstanceOf(TicketNotFoundException.class);

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void updateAndCommentCreationOwnWriteTransactions() throws Exception {
        Method updateMethod = TicketService.class.getMethod(
                "updateTicket", Long.class, UpdateTicketRequest.class);
        Method commentMethod = TicketService.class.getMethod(
                "addComment", Long.class, AddCommentRequest.class);
        Transactional updateTransaction =
                AnnotatedElementUtils.findMergedAnnotation(updateMethod, Transactional.class);
        Transactional commentTransaction =
                AnnotatedElementUtils.findMergedAnnotation(commentMethod, Transactional.class);

        assertThat(updateTransaction).isNotNull();
        assertThat(updateTransaction.readOnly()).isFalse();
        assertThat(commentTransaction).isNotNull();
        assertThat(commentTransaction.readOnly()).isFalse();
    }

    private Ticket ticket(
            Long id,
            String title,
            String description,
            TicketStatus status) {
        Ticket ticket = new Ticket(title, description, Priority.MEDIUM, null);
        ticket.setStatus(status);
        ReflectionTestUtils.setField(ticket, "id", id);
        return ticket;
    }

    private Comment comment(
            Long id,
            Ticket ticket,
            String body,
            String timestamp) {
        Comment comment = new Comment(ticket, body, Instant.parse(timestamp));
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }
}
