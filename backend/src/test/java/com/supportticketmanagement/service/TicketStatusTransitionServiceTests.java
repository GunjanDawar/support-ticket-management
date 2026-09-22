package com.supportticketmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.stream.Stream;

import com.supportticketmanagement.dto.StatusTransitionRequest;
import com.supportticketmanagement.dto.TicketResponse;
import com.supportticketmanagement.error.InvalidStatusTransitionException;
import com.supportticketmanagement.error.TicketNotFoundException;
import com.supportticketmanagement.persistence.Priority;
import com.supportticketmanagement.persistence.Ticket;
import com.supportticketmanagement.persistence.TicketStatus;
import com.supportticketmanagement.repository.CommentRepository;
import com.supportticketmanagement.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TicketStatusTransitionServiceTests {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private CommentRepository commentRepository;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, commentRepository);
    }

    @ParameterizedTest(name = "{0} to {1}: allowed={2}")
    @MethodSource("completeTransitionMatrix")
    void enforcesCompleteTransitionMatrix(
            TicketStatus current,
            TicketStatus target,
            boolean allowed) {
        Ticket ticket = ticket(current);
        when(ticketRepository.findById(42L)).thenReturn(Optional.of(ticket));
        if (allowed) {
            when(ticketRepository.save(ticket)).thenReturn(ticket);

            TicketResponse response =
                    ticketService.changeStatus(42L, new StatusTransitionRequest(target));

            verify(ticketRepository).save(ticket);
            assertThat(ticket.getStatus()).isEqualTo(target);
            assertThat(response.getStatus()).isEqualTo(target);
            assertUnchangedNonStatusFields(ticket);
        } else {
            assertThatThrownBy(() ->
                    ticketService.changeStatus(42L, new StatusTransitionRequest(target)))
                    .isInstanceOf(InvalidStatusTransitionException.class);

            verify(ticketRepository, never()).save(any(Ticket.class));
            assertThat(ticket.getStatus()).isEqualTo(current);
            assertUnchangedNonStatusFields(ticket);
        }
    }

    @Test
    void missingTicketThrowsBeforeTransitionValidationOrSave() {
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.changeStatus(
                999L,
                new StatusTransitionRequest(TicketStatus.IN_PROGRESS)))
                .isInstanceOf(TicketNotFoundException.class);

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void statusChangeOwnsWriteTransaction() throws Exception {
        Method method = TicketService.class.getMethod(
                "changeStatus", Long.class, StatusTransitionRequest.class);
        Transactional transaction =
                AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isFalse();
    }

    private Ticket ticket(TicketStatus status) {
        Ticket ticket = new Ticket(
                "Original title",
                "Original description",
                Priority.HIGH,
                "Original assignee");
        ticket.setStatus(status);
        ReflectionTestUtils.setField(ticket, "id", 42L);
        return ticket;
    }

    private void assertUnchangedNonStatusFields(Ticket ticket) {
        assertThat(ticket.getId()).isEqualTo(42L);
        assertThat(ticket.getTitle()).isEqualTo("Original title");
        assertThat(ticket.getDescription()).isEqualTo("Original description");
        assertThat(ticket.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(ticket.getAssignee()).isEqualTo("Original assignee");
    }

    private static Stream<Arguments> completeTransitionMatrix() {
        return Stream.of(
                Arguments.of(TicketStatus.OPEN, TicketStatus.OPEN, false),
                Arguments.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, true),
                Arguments.of(TicketStatus.OPEN, TicketStatus.RESOLVED, false),
                Arguments.of(TicketStatus.OPEN, TicketStatus.CLOSED, false),
                Arguments.of(TicketStatus.OPEN, TicketStatus.CANCELLED, true),

                Arguments.of(TicketStatus.IN_PROGRESS, TicketStatus.OPEN, false),
                Arguments.of(TicketStatus.IN_PROGRESS, TicketStatus.IN_PROGRESS, false),
                Arguments.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, true),
                Arguments.of(TicketStatus.IN_PROGRESS, TicketStatus.CLOSED, false),
                Arguments.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED, true),

                Arguments.of(TicketStatus.RESOLVED, TicketStatus.OPEN, false),
                Arguments.of(TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS, false),
                Arguments.of(TicketStatus.RESOLVED, TicketStatus.RESOLVED, false),
                Arguments.of(TicketStatus.RESOLVED, TicketStatus.CLOSED, true),
                Arguments.of(TicketStatus.RESOLVED, TicketStatus.CANCELLED, false),

                Arguments.of(TicketStatus.CLOSED, TicketStatus.OPEN, false),
                Arguments.of(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS, false),
                Arguments.of(TicketStatus.CLOSED, TicketStatus.RESOLVED, false),
                Arguments.of(TicketStatus.CLOSED, TicketStatus.CLOSED, false),
                Arguments.of(TicketStatus.CLOSED, TicketStatus.CANCELLED, false),

                Arguments.of(TicketStatus.CANCELLED, TicketStatus.OPEN, false),
                Arguments.of(TicketStatus.CANCELLED, TicketStatus.IN_PROGRESS, false),
                Arguments.of(TicketStatus.CANCELLED, TicketStatus.RESOLVED, false),
                Arguments.of(TicketStatus.CANCELLED, TicketStatus.CLOSED, false),
                Arguments.of(TicketStatus.CANCELLED, TicketStatus.CANCELLED, false));
    }
}
