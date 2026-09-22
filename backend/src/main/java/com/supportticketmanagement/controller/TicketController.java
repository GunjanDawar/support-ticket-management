package com.supportticketmanagement.controller;

import java.util.List;

import com.supportticketmanagement.dto.AddCommentRequest;
import com.supportticketmanagement.dto.CommentResponse;
import com.supportticketmanagement.dto.CreateTicketRequest;
import com.supportticketmanagement.dto.StatusTransitionRequest;
import com.supportticketmanagement.dto.TicketDetailResponse;
import com.supportticketmanagement.dto.TicketResponse;
import com.supportticketmanagement.dto.TicketSummaryResponse;
import com.supportticketmanagement.dto.UpdateTicketRequest;
import com.supportticketmanagement.persistence.TicketStatus;
import com.supportticketmanagement.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse createTicket(
            @Valid @RequestBody CreateTicketRequest request) {
        return ticketService.createTicket(request);
    }

    @GetMapping
    public List<TicketSummaryResponse> listTickets(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) TicketStatus status) {
        return ticketService.listTickets(keyword, status);
    }

    @GetMapping("/{id}")
    public TicketDetailResponse getTicketDetail(@PathVariable Long id) {
        return ticketService.getTicketDetail(id);
    }

    @PatchMapping("/{id}")
    public TicketResponse updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketRequest request) {
        return ticketService.updateTicket(id, request);
    }

    @PatchMapping("/{id}/status")
    public TicketResponse changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusTransitionRequest request) {
        return ticketService.changeStatus(id, request);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(
            @PathVariable Long id,
            @Valid @RequestBody AddCommentRequest request) {
        return ticketService.addComment(id, request);
    }
}
