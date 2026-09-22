package com.supportticketmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportticketmanagement.dto.AddCommentRequest;
import com.supportticketmanagement.dto.CreateTicketRequest;
import com.supportticketmanagement.dto.StatusTransitionRequest;
import com.supportticketmanagement.dto.TicketResponse;
import com.supportticketmanagement.dto.UpdateTicketRequest;
import com.supportticketmanagement.error.InvalidStatusTransitionException;
import com.supportticketmanagement.error.TerminalTicketConflictException;
import com.supportticketmanagement.error.TicketNotFoundException;
import com.supportticketmanagement.persistence.Priority;
import com.supportticketmanagement.persistence.TicketStatus;
import com.supportticketmanagement.service.TicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(TicketController.class)
class TicketControllerContractCoverageTests {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @MockitoBean
    private TicketService ticketService;

    @Autowired
    TicketControllerContractCoverageTests(
            MockMvc mockMvc,
            ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void createWithoutOptionalAssigneeBindsRequestAndReturnsJson() throws Exception {
        when(ticketService.createTicket(any(CreateTicketRequest.class))).thenReturn(
                ticketResponse(42L, TicketStatus.OPEN));

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Title",
                                  "description": "Description",
                                  "priority": "HIGH"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        ArgumentCaptor<CreateTicketRequest> captor =
                ArgumentCaptor.forClass(CreateTicketRequest.class);
        verify(ticketService).createTicket(captor.capture());
        assertThat(captor.getValue().title()).isEqualTo("Title");
        assertThat(captor.getValue().description()).isEqualTo("Description");
        assertThat(captor.getValue().priority()).isEqualTo(Priority.HIGH);
        assertThat(captor.getValue().assignee()).isNull();
    }

    @ParameterizedTest(name = "create rejects {0}")
    @MethodSource("invalidCreateRequests")
    void createRejectsRemainingValidationAndSyntaxFailures(String caseName, String body)
            throws Exception {
        assertValidationError(mockMvc.perform(post("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)));
        verifyNoInteractions(ticketService);
    }

    @Test
    void listAcceptsEmptyKeywordAndReturnsEmptyJsonArray() throws Exception {
        when(ticketService.listTickets("", null)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/tickets").queryParam("keyword", ""))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(ticketService).listTickets("", null);
    }

    @Test
    void ordinaryPatchBindsAllApprovedFieldsAndExplicitNullAssignee() throws Exception {
        when(ticketService.updateTicket(
                org.mockito.ArgumentMatchers.eq(42L),
                any(UpdateTicketRequest.class)))
                .thenReturn(ticketResponse(42L, TicketStatus.IN_PROGRESS));

        mockMvc.perform(patch("/api/tickets/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated title",
                                  "description": "Updated description",
                                  "priority": "LOW",
                                  "assignee": null
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateTicketRequest> captor =
                ArgumentCaptor.forClass(UpdateTicketRequest.class);
        verify(ticketService).updateTicket(
                org.mockito.ArgumentMatchers.eq(42L),
                captor.capture());
        UpdateTicketRequest request = captor.getValue();
        assertThat(request.isTitlePresent()).isTrue();
        assertThat(request.isDescriptionPresent()).isTrue();
        assertThat(request.isPriorityPresent()).isTrue();
        assertThat(request.isAssigneePresent()).isTrue();
        assertThat(request.getTitle()).isEqualTo("Updated title");
        assertThat(request.getDescription()).isEqualTo("Updated description");
        assertThat(request.getPriority()).isEqualTo(Priority.LOW);
        assertThat(request.getAssignee()).isNull();
    }

    @ParameterizedTest(name = "ordinary PATCH rejects forbidden property {0}")
    @MethodSource("forbiddenPatchRequests")
    void ordinaryPatchRejectsEveryUnsupportedField(String field, String body)
            throws Exception {
        assertValidationError(mockMvc.perform(patch("/api/tickets/42")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)));
        verifyNoInteractions(ticketService);
    }

    @ParameterizedTest(name = "ordinary PATCH rejects {0}")
    @MethodSource("invalidPatchValues")
    void ordinaryPatchRejectsRemainingInvalidValues(String caseName, String body)
            throws Exception {
        assertValidationError(mockMvc.perform(patch("/api/tickets/42")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)));
        verifyNoInteractions(ticketService);
    }

    @ParameterizedTest(name = "status PATCH rejects {0}")
    @MethodSource("invalidStatusRequests")
    void statusPatchRejectsMissingNullAndUnknownProperties(String caseName, String body)
            throws Exception {
        assertValidationError(mockMvc.perform(patch("/api/tickets/42/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)));
        verifyNoInteractions(ticketService);
    }

    @Test
    void commentEndpointRejectsMalformedJson() throws Exception {
        assertValidationError(mockMvc.perform(post("/api/tickets/42/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":")));
        verifyNoInteractions(ticketService);
    }

    @Test
    void representativeErrorsExposeExactlyFrozenShapeAndMappings() throws Exception {
        when(ticketService.getTicketDetail(999L))
                .thenThrow(new TicketNotFoundException(999L));
        when(ticketService.changeStatus(
                org.mockito.ArgumentMatchers.eq(42L),
                any(StatusTransitionRequest.class)))
                .thenThrow(new InvalidStatusTransitionException(
                        TicketStatus.OPEN, TicketStatus.CLOSED));
        when(ticketService.addComment(
                org.mockito.ArgumentMatchers.eq(42L),
                any(AddCommentRequest.class)))
                .thenThrow(new TerminalTicketConflictException(42L, TicketStatus.CLOSED));

        MvcResult validation = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        MvcResult missing = mockMvc.perform(get("/api/tickets/999"))
                .andExpect(status().isNotFound())
                .andReturn();
        MvcResult invalidTransition = mockMvc.perform(patch("/api/tickets/42/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus": "CLOSED"}
                                """))
                .andExpect(status().isConflict())
                .andReturn();
        MvcResult terminalConflict = mockMvc.perform(post("/api/tickets/42/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "Comment"}
                                """))
                .andExpect(status().isConflict())
                .andReturn();

        assertError(validation, "VALIDATION_ERROR", 400, "/api/tickets");
        assertError(missing, "TICKET_NOT_FOUND", 404, "/api/tickets/999");
        assertError(
                invalidTransition,
                "INVALID_STATUS_TRANSITION",
                409,
                "/api/tickets/42/status");
        assertError(
                terminalConflict,
                "TERMINAL_TICKET_CONFLICT",
                409,
                "/api/tickets/42/comments");
    }

    private void assertValidationError(
            org.springframework.test.web.servlet.ResultActions resultActions)
            throws Exception {
        MvcResult result = resultActions
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode json = responseJson(result);
        assertThat(json.get("code").textValue()).isEqualTo("VALIDATION_ERROR");
        assertThat(json.get("status").intValue()).isEqualTo(400);
    }

    private void assertError(
            MvcResult result,
            String code,
            int status,
            String path) throws Exception {
        JsonNode json = responseJson(result);
        assertThat(fieldNames(json))
                .containsExactlyInAnyOrder("code", "message", "status", "path");
        assertThat(json.get("code").textValue()).isEqualTo(code);
        assertThat(json.get("message").textValue()).isNotBlank();
        assertThat(json.get("status").intValue()).isEqualTo(status);
        assertThat(json.get("path").textValue()).isEqualTo(path);
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private Set<String> fieldNames(JsonNode json) {
        Set<String> fields = new HashSet<>();
        json.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private TicketResponse ticketResponse(Long id, TicketStatus status) {
        return new TicketResponse(
                id,
                "Title",
                "Description",
                Priority.HIGH,
                null,
                status);
    }

    private static Stream<Arguments> invalidCreateRequests() {
        return Stream.of(
                Arguments.of(
                        "missing description",
                        """
                        {"title": "Title", "priority": "HIGH"}
                        """),
                Arguments.of(
                        "missing priority",
                        """
                        {"title": "Title", "description": "Description"}
                        """),
                Arguments.of(
                        "title over 200 characters",
                        createJson("t".repeat(201), "Description", "HIGH")),
                Arguments.of(
                        "description over 5000 characters",
                        createJson("Title", "d".repeat(5_001), "HIGH")),
                Arguments.of(
                        "invalid priority",
                        createJson("Title", "Description", "URGENT")),
                Arguments.of(
                        "malformed JSON",
                        "{\"title\":"));
    }

    private static Stream<Arguments> forbiddenPatchRequests() {
        return Stream.of(
                Arguments.of("status", "{\"status\":\"RESOLVED\"}"),
                Arguments.of("id", "{\"id\":\"42\"}"),
                Arguments.of("comments", "{\"comments\":[]}"),
                Arguments.of("timestamp", "{\"timestamp\":\"2026-09-22T10:15:30Z\"}"),
                Arguments.of("targetStatus", "{\"targetStatus\":\"RESOLVED\"}"),
                Arguments.of("unknownField", "{\"unknownField\":\"value\"}"));
    }

    private static Stream<Arguments> invalidPatchValues() {
        return Stream.of(
                Arguments.of("title over 200", "{\"title\":\"%s\"}"
                        .formatted("t".repeat(201))),
                Arguments.of("description over 5000", "{\"description\":\"%s\"}"
                        .formatted("d".repeat(5_001))),
                Arguments.of("null title", "{\"title\":null}"),
                Arguments.of("null description", "{\"description\":null}"),
                Arguments.of("null priority", "{\"priority\":null}"),
                Arguments.of("invalid priority", "{\"priority\":\"URGENT\"}"));
    }

    private static Stream<Arguments> invalidStatusRequests() {
        return Stream.of(
                Arguments.of("missing targetStatus", "{}"),
                Arguments.of("null targetStatus", "{\"targetStatus\":null}"),
                Arguments.of(
                        "unknown field",
                        "{\"targetStatus\":\"OPEN\",\"status\":\"OPEN\"}"));
    }

    private static String createJson(String title, String description, String priority) {
        return """
                {"title":"%s","description":"%s","priority":"%s"}
                """.formatted(title, description, priority);
    }
}
