package com.supportticketmanagement.service;

import java.time.Instant;
import java.util.List;

import com.supportticketmanagement.dto.AddCommentRequest;
import com.supportticketmanagement.dto.CommentResponse;
import com.supportticketmanagement.dto.CreateTicketRequest;
import com.supportticketmanagement.dto.StatusTransitionRequest;
import com.supportticketmanagement.dto.TicketDetailResponse;
import com.supportticketmanagement.dto.TicketResponse;
import com.supportticketmanagement.dto.TicketSummaryResponse;
import com.supportticketmanagement.dto.UpdateTicketRequest;
import com.supportticketmanagement.error.InvalidStatusTransitionException;
import com.supportticketmanagement.error.TerminalTicketConflictException;
import com.supportticketmanagement.error.TicketNotFoundException;
import com.supportticketmanagement.persistence.Comment;
import com.supportticketmanagement.persistence.Ticket;
import com.supportticketmanagement.persistence.TicketStatus;
import com.supportticketmanagement.repository.CommentRepository;
import com.supportticketmanagement.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;

    public TicketService(
            TicketRepository ticketRepository,
            CommentRepository commentRepository) {
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        Ticket ticket = new Ticket(
                request.title(),
                request.description(),
                request.priority(),
                request.assignee());
        ticket.setStatus(TicketStatus.OPEN);

        return toTicketResponse(ticketRepository.save(ticket));
    }

    @Transactional(readOnly = true)
    public List<TicketSummaryResponse> listTickets(
            String keyword,
            TicketStatus status) {
        List<Ticket> tickets;

        if (keyword == null || keyword.isEmpty()) {
            tickets = status == null
                    ? ticketRepository.findAll()
                    : ticketRepository.findAllByStatus(status);
        } else {
            tickets = status == null
                    ? ticketRepository.searchByKeyword(keyword)
                    : ticketRepository.searchByKeywordAndStatus(keyword, status);
        }

        return tickets.stream()
                .map(this::toTicketSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketDetailResponse getTicketDetail(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
        List<CommentResponse> comments = commentRepository.findAllByTicket(ticket).stream()
                .map(this::toCommentResponse)
                .toList();

        return toTicketDetailResponse(ticket, comments);
    }

    @Transactional
    public TicketResponse updateTicket(Long id, UpdateTicketRequest request) {
        Ticket ticket = findTicket(id);
        ensureNonTerminal(ticket);

        if (request.isTitlePresent()) {
            ticket.setTitle(request.getTitle());
        }
        if (request.isDescriptionPresent()) {
            ticket.setDescription(request.getDescription());
        }
        if (request.isPriorityPresent()) {
            ticket.setPriority(request.getPriority());
        }
        if (request.isAssigneePresent()) {
            ticket.setAssignee(request.getAssignee());
        }

        return toTicketResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public CommentResponse addComment(Long ticketId, AddCommentRequest request) {
        Ticket ticket = findTicket(ticketId);
        ensureNonTerminal(ticket);

        Comment comment = new Comment(ticket, request.body(), Instant.now());
        return toCommentResponse(commentRepository.save(comment));
    }

    @Transactional
    public TicketResponse changeStatus(Long ticketId, StatusTransitionRequest request) {
        Ticket ticket = findTicket(ticketId);
        TicketStatus currentStatus = ticket.getStatus();
        TicketStatus targetStatus = request.targetStatus();

        if (!isAllowedTransition(currentStatus, targetStatus)) {
            throw new InvalidStatusTransitionException(currentStatus, targetStatus);
        }

        ticket.setStatus(targetStatus);
        return toTicketResponse(ticketRepository.save(ticket));
    }

    private boolean isAllowedTransition(
            TicketStatus currentStatus,
            TicketStatus targetStatus) {
        return switch (currentStatus) {
            case OPEN ->
                    targetStatus == TicketStatus.IN_PROGRESS
                            || targetStatus == TicketStatus.CANCELLED;
            case IN_PROGRESS ->
                    targetStatus == TicketStatus.RESOLVED
                            || targetStatus == TicketStatus.CANCELLED;
            case RESOLVED -> targetStatus == TicketStatus.CLOSED;
            case CLOSED, CANCELLED -> false;
        };
    }

    private Ticket findTicket(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
    }

    private void ensureNonTerminal(Ticket ticket) {
        if (ticket.getStatus() == TicketStatus.CLOSED
                || ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new TerminalTicketConflictException(ticket.getId(), ticket.getStatus());
        }
    }

    private TicketResponse toTicketResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getPriority(),
                ticket.getAssignee(),
                ticket.getStatus());
    }

    private TicketSummaryResponse toTicketSummaryResponse(Ticket ticket) {
        return new TicketSummaryResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getPriority(),
                ticket.getAssignee(),
                ticket.getStatus());
    }

    private TicketDetailResponse toTicketDetailResponse(
            Ticket ticket,
            List<CommentResponse> comments) {
        return new TicketDetailResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getPriority(),
                ticket.getAssignee(),
                ticket.getStatus(),
                comments);
    }

    private CommentResponse toCommentResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getBody(),
                comment.getTimestamp());
    }
}
