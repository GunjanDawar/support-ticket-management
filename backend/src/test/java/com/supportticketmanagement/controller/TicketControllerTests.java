package com.supportticketmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.supportticketmanagement.persistence.Priority;
import com.supportticketmanagement.persistence.TicketStatus;
import com.supportticketmanagement.service.TicketService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(TicketController.class)
class TicketControllerTests {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private TicketService ticketService;

    @Autowired
    TicketControllerTests(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void createReturns201AndExactTicketResource() throws Exception {
        when(ticketService.createTicket(any(CreateTicketRequest.class))).thenReturn(
                ticketResponse(42L, TicketStatus.OPEN));

        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Printer not working",
                                  "description": "The printer is showing an error",
                                  "priority": "HIGH",
                                  "assignee": "john"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = responseJson(result);
        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "id", "title", "description", "priority", "assignee", "status");
        assertThat(json.get("id").isTextual()).isTrue();
        assertThat(json.get("id").textValue()).isEqualTo("42");
        assertThat(json.get("status").textValue()).isEqualTo("OPEN");
        verify(ticketService).createTicket(any(CreateTicketRequest.class));
    }

    @Test
    void createValidationFailureReturnsApproved400WithoutDelegation() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Missing title", "priority": "HIGH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/tickets"));

        verifyNoInteractions(ticketService);
    }

    @Test
    void createUnknownFieldReturnsApproved400() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Title",
                                  "description": "Description",
                                  "priority": "HIGH",
                                  "status": "CLOSED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listWithoutFiltersReturnsSummaryArray() throws Exception {
        when(ticketService.listTickets(null, null)).thenReturn(List.of(
                new TicketSummaryResponse(
                        42L, "Printer", Priority.HIGH, null, TicketStatus.OPEN)));

        MvcResult result = mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode item = responseJson(result).get(0);
        assertThat(fieldNames(item)).containsExactlyInAnyOrder(
                "id", "title", "priority", "assignee", "status");
        assertThat(item.has("description")).isFalse();
        assertThat(item.has("comments")).isFalse();
        verify(ticketService).listTickets(null, null);
    }

    @Test
    void listForwardsKeyword() throws Exception {
        when(ticketService.listTickets("printer", null)).thenReturn(List.of());

        mockMvc.perform(get("/api/tickets").param("keyword", "printer"))
                .andExpect(status().isOk());

        verify(ticketService).listTickets("printer", null);
    }

    @Test
    void listForwardsStatusEnum() throws Exception {
        when(ticketService.listTickets(null, TicketStatus.OPEN)).thenReturn(List.of());

        mockMvc.perform(get("/api/tickets").param("status", "OPEN"))
                .andExpect(status().isOk());

        verify(ticketService).listTickets(null, TicketStatus.OPEN);
    }

    @Test
    void listForwardsCombinedKeywordAndStatus() throws Exception {
        when(ticketService.listTickets("printer", TicketStatus.OPEN)).thenReturn(List.of());

        mockMvc.perform(get("/api/tickets")
                        .param("keyword", "printer")
                        .param("status", "OPEN"))
                .andExpect(status().isOk());

        verify(ticketService).listTickets("printer", TicketStatus.OPEN);
    }

    @Test
    void invalidStatusQueryReturnsApproved400() throws Exception {
        mockMvc.perform(get("/api/tickets").param("status", "ACTIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void detailReturnsExactTicketDetailAndNestedComment() throws Exception {
        when(ticketService.getTicketDetail(42L)).thenReturn(new TicketDetailResponse(
                42L,
                "Printer",
                "Printer description",
                Priority.HIGH,
                "john",
                TicketStatus.OPEN,
                List.of(new CommentResponse(
                        84L,
                        "Investigating",
                        Instant.parse("2026-09-22T10:15:30Z")))));

        MvcResult result = mockMvc.perform(get("/api/tickets/42"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = responseJson(result);
        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "id", "title", "description", "priority", "assignee", "status", "comments");
        assertThat(fieldNames(json.get("comments").get(0)))
                .containsExactlyInAnyOrder("id", "body", "timestamp");
        verify(ticketService).getTicketDetail(42L);
    }

    @Test
    void detailMissingTicketReturnsApproved404() throws Exception {
        when(ticketService.getTicketDetail(999L)).thenThrow(new TicketNotFoundException(999L));

        mockMvc.perform(get("/api/tickets/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/tickets/999"));
    }

    @Test
    void malformedPathIdReturnsApproved400() throws Exception {
        mockMvc.perform(get("/api/tickets/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/tickets/not-a-number"));
    }

    @Test
    void ordinaryPatchReturnsExactTicketResourceAndDelegates() throws Exception {
        when(ticketService.updateTicket(
                org.mockito.ArgumentMatchers.eq(42L),
                any(UpdateTicketRequest.class)))
                .thenReturn(ticketResponse(42L, TicketStatus.OPEN));

        MvcResult result = mockMvc.perform(patch("/api/tickets/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Updated title"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(fieldNames(responseJson(result))).containsExactlyInAnyOrder(
                "id", "title", "description", "priority", "assignee", "status");
        verify(ticketService).updateTicket(
                org.mockito.ArgumentMatchers.eq(42L),
                any(UpdateTicketRequest.class));
    }

    @Test
    void emptyOrdinaryPatchReturnsApproved400() throws Exception {
        mockMvc.perform(patch("/api/tickets/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void ordinaryPatchMissingTicketReturnsApproved404() throws Exception {
        when(ticketService.updateTicket(
                org.mockito.ArgumentMatchers.eq(999L),
                any(UpdateTicketRequest.class)))
                .thenThrow(new TicketNotFoundException(999L));

        mockMvc.perform(patch("/api/tickets/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Updated title"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
    }

    @Test
    void ordinaryPatchTerminalConflictReturnsApproved409() throws Exception {
        when(ticketService.updateTicket(
                org.mockito.ArgumentMatchers.eq(42L),
                any(UpdateTicketRequest.class)))
                .thenThrow(new TerminalTicketConflictException(42L, TicketStatus.CLOSED));

        mockMvc.perform(patch("/api/tickets/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Updated title"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TERMINAL_TICKET_CONFLICT"));
    }

    @Test
    void statusPatchReturnsExactTicketResourceAndDelegates() throws Exception {
        when(ticketService.changeStatus(
                org.mockito.ArgumentMatchers.eq(42L),
                any(StatusTransitionRequest.class)))
                .thenReturn(ticketResponse(42L, TicketStatus.IN_PROGRESS));

        MvcResult result = mockMvc.perform(patch("/api/tickets/42/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus": "IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = responseJson(result);
        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "id", "title", "description", "priority", "assignee", "status");
        assertThat(json.get("status").textValue()).isEqualTo("IN_PROGRESS");
        verify(ticketService).changeStatus(
                org.mockito.ArgumentMatchers.eq(42L),
                any(StatusTransitionRequest.class));
    }

    @Test
    void invalidTransitionReturnsApproved409() throws Exception {
        when(ticketService.changeStatus(
                org.mockito.ArgumentMatchers.eq(42L),
                any(StatusTransitionRequest.class)))
                .thenThrow(new InvalidStatusTransitionException(
                        TicketStatus.OPEN, TicketStatus.CLOSED));

        mockMvc.perform(patch("/api/tickets/42/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus": "CLOSED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void statusPatchMissingTicketReturnsApproved404() throws Exception {
        when(ticketService.changeStatus(
                org.mockito.ArgumentMatchers.eq(999L),
                any(StatusTransitionRequest.class)))
                .thenThrow(new TicketNotFoundException(999L));

        mockMvc.perform(patch("/api/tickets/999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus": "IN_PROGRESS"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
    }

    @Test
    void malformedStatusRequestReturnsApproved400() throws Exception {
        mockMvc.perform(patch("/api/tickets/42/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus": "PAUSED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void addCommentReturns201AndExactCommentResource() throws Exception {
        when(ticketService.addComment(
                org.mockito.ArgumentMatchers.eq(42L),
                any(AddCommentRequest.class)))
                .thenReturn(new CommentResponse(
                        84L,
                        "Investigated.",
                        Instant.parse("2026-09-22T10:15:30Z")));

        MvcResult result = mockMvc.perform(post("/api/tickets/42/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "Investigated."}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = responseJson(result);
        assertThat(fieldNames(json)).containsExactlyInAnyOrder("id", "body", "timestamp");
        assertThat(json.get("id").isTextual()).isTrue();
        assertThat(json.get("id").textValue()).isEqualTo("84");
        assertThat(json.get("body").textValue()).isEqualTo("Investigated.");
        assertThat(json.get("timestamp").textValue()).isEqualTo("2026-09-22T10:15:30Z");
        verify(ticketService).addComment(
                org.mockito.ArgumentMatchers.eq(42L),
                any(AddCommentRequest.class));
    }

    @Test
    void addCommentMissingBodyReturnsApproved400WithoutDelegation() throws Exception {
        mockMvc.perform(post("/api/tickets/42/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(ticketService);
    }

    @Test
    void addCommentNullBodyReturnsApproved400WithoutDelegation() throws Exception {
        mockMvc.perform(post("/api/tickets/42/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(ticketService);
    }

    @Test
    void addCommentUnknownFieldReturnsApproved400WithoutDelegation() throws Exception {
        mockMvc.perform(post("/api/tickets/42/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "Investigated.", "author": "John"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(ticketService);
    }

    @Test
    void addCommentMalformedTicketIdReturnsApproved400WithoutDelegation() throws Exception {
        mockMvc.perform(post("/api/tickets/not-a-number/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "Investigated."}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path")
                        .value("/api/tickets/not-a-number/comments"));

        verifyNoInteractions(ticketService);
    }

    @Test
    void addCommentMissingTicketReturnsApproved404() throws Exception {
        when(ticketService.addComment(
                org.mockito.ArgumentMatchers.eq(999L),
                any(AddCommentRequest.class)))
                .thenThrow(new TicketNotFoundException(999L));

        mockMvc.perform(post("/api/tickets/999/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "Investigated."}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void addCommentTerminalTicketReturnsApproved409() throws Exception {
        when(ticketService.addComment(
                org.mockito.ArgumentMatchers.eq(42L),
                any(AddCommentRequest.class)))
                .thenThrow(new TerminalTicketConflictException(42L, TicketStatus.CLOSED));

        mockMvc.perform(post("/api/tickets/42/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "Investigated."}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TERMINAL_TICKET_CONFLICT"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void addCommentPreservesBodyExactlyWhenDelegating() throws Exception {
        String body = "  Investigated by support team.  ";
        when(ticketService.addComment(
                org.mockito.ArgumentMatchers.eq(42L),
                any(AddCommentRequest.class)))
                .thenReturn(new CommentResponse(
                        84L,
                        body,
                        Instant.parse("2026-09-22T10:15:30Z")));

        mockMvc.perform(post("/api/tickets/42/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "  Investigated by support team.  "}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<AddCommentRequest> requestCaptor =
                ArgumentCaptor.forClass(AddCommentRequest.class);
        verify(ticketService).addComment(
                org.mockito.ArgumentMatchers.eq(42L),
                requestCaptor.capture());
        assertThat(requestCaptor.getValue().body()).isEqualTo(body);
    }

    private TicketResponse ticketResponse(Long id, TicketStatus status) {
        return new TicketResponse(
                id,
                "Printer not working",
                "The printer is showing an error",
                Priority.HIGH,
                "john",
                status);
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private Set<String> fieldNames(JsonNode json) {
        Set<String> fields = new HashSet<>();
        json.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
