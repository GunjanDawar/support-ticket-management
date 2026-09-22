package com.supportticketmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportticketmanagement.repository.CommentRepository;
import com.supportticketmanagement.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendIntegrationTests {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;

    @Autowired
    BackendIntegrationTests(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            TicketRepository ticketRepository,
            CommentRepository commentRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
    }

    @BeforeEach
    void clearDatabase() {
        commentRepository.deleteAll();
        ticketRepository.deleteAll();
    }

    @Test
    void createThenRetrievePersistsTicketAcrossRequests() throws Exception {
        JsonNode created = createTicket(
                "Integration ticket",
                "Created through the real REST stack",
                "HIGH",
                "Alice");

        assertThat(created.get("id").isTextual()).isTrue();
        assertThat(created.get("status").textValue()).isEqualTo("OPEN");
        JsonNode retrieved = getTicket(created.get("id").textValue());
        assertThat(retrieved.get("title").textValue()).isEqualTo("Integration ticket");
        assertThat(retrieved.get("description").textValue())
                .isEqualTo("Created through the real REST stack");
        assertThat(retrieved.get("priority").textValue()).isEqualTo("HIGH");
        assertThat(retrieved.get("assignee").textValue()).isEqualTo("Alice");
        assertThat(retrieved.get("status").textValue()).isEqualTo("OPEN");
    }

    @Test
    void createWithoutAssigneePersistsNullAcrossRequests() throws Exception {
        JsonNode created = createTicket("Unassigned", "Description", "LOW", null);
        assertThat(created.get("assignee").isNull()).isTrue();

        JsonNode retrieved = getTicket(created.get("id").textValue());
        assertThat(retrieved.get("assignee").isNull()).isTrue();
    }

    @Test
    void listReturnsCreatedTicketsAsSummaries() throws Exception {
        String firstId = createTicket("First", "First description", "LOW", null)
                .get("id").textValue();
        String secondId = createTicket("Second", "Second description", "HIGH", "Alice")
                .get("id").textValue();

        JsonNode list = responseJson(mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(ids(list)).containsExactlyInAnyOrder(firstId, secondId);
        list.forEach(item -> {
            assertThat(fieldNames(item)).containsExactlyInAnyOrder(
                    "id", "title", "priority", "assignee", "status");
            assertThat(item.get("id").isTextual()).isTrue();
            assertThat(item.has("description")).isFalse();
        });
    }

    @Test
    void searchMatchesTitleOrDescriptionCaseInsensitively() throws Exception {
        String titleId = createTicket(
                "Login failure", "Authentication problem", "HIGH", null)
                .get("id").textValue();
        String descriptionId = createTicket(
                "Payment issue", "Login-related investigation", "MEDIUM", null)
                .get("id").textValue();
        createTicket("Dashboard issue", "Unrelated", "LOW", null);

        JsonNode result = responseJson(mockMvc.perform(
                        get("/api/tickets").queryParam("keyword", "LoGiN"))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(ids(result)).containsExactlyInAnyOrder(titleId, descriptionId);
    }

    @Test
    void statusFilterUsesPersistedStatus() throws Exception {
        String openId = createTicket("Open", "Description", "LOW", null)
                .get("id").textValue();
        String inProgressId = createTicket("Progress", "Description", "LOW", null)
                .get("id").textValue();
        changeStatus(inProgressId, "IN_PROGRESS");

        JsonNode result = responseJson(mockMvc.perform(
                        get("/api/tickets").queryParam("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(ids(result)).containsExactly(inProgressId);
        assertThat(ids(result)).doesNotContain(openId);
    }

    @Test
    void combinedSearchAndStatusRequiresBothCriteria() throws Exception {
        createTicket("Login issue", "Keyword but wrong status", "LOW", null);
        String statusOnlyId = createTicket(
                "Printer issue", "No matching text", "LOW", null)
                .get("id").textValue();
        changeStatus(statusOnlyId, "IN_PROGRESS");
        String bothId = createTicket(
                "Account issue", "Login investigation", "LOW", null)
                .get("id").textValue();
        changeStatus(bothId, "IN_PROGRESS");

        JsonNode result = responseJson(mockMvc.perform(get("/api/tickets")
                        .queryParam("keyword", "login")
                        .queryParam("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(ids(result)).containsExactly(bothId);
    }

    @Test
    void ordinaryUpdateAndNullAssigneePersistWhileStatusRemainsUnchanged() throws Exception {
        String id = createTicket("Original", "Original description", "HIGH", "Alice")
                .get("id").textValue();
        changeStatus(id, "IN_PROGRESS");

        JsonNode updated = responseJson(mockMvc.perform(patch("/api/tickets/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "title", "Updated title",
                                "priority", "CRITICAL",
                                "assignee", "Bob"))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(updated.get("description").textValue()).isEqualTo("Original description");
        assertThat(updated.get("status").textValue()).isEqualTo("IN_PROGRESS");

        mockMvc.perform(patch("/api/tickets/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":null}"))
                .andExpect(status().isOk());

        JsonNode retrieved = getTicket(id);
        assertThat(retrieved.get("title").textValue()).isEqualTo("Updated title");
        assertThat(retrieved.get("priority").textValue()).isEqualTo("CRITICAL");
        assertThat(retrieved.get("assignee").isNull()).isTrue();
        assertThat(retrieved.get("status").textValue()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void commentCreationPersistsIntoLaterTicketDetail() throws Exception {
        String id = createTicket("Comment owner", "Description", "MEDIUM", null)
                .get("id").textValue();

        JsonNode comment = responseJson(mockMvc.perform(
                        post("/api/tickets/{id}/comments", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("body", "Investigated the issue."))))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(comment.get("id").isTextual()).isTrue();
        assertThat(comment.get("body").textValue()).isEqualTo("Investigated the issue.");
        assertThat(comment.get("timestamp").textValue()).isNotBlank();

        JsonNode detail = getTicket(id);
        assertThat(detail.get("comments")).anySatisfy(stored -> {
            assertThat(stored.get("id").textValue()).isEqualTo(comment.get("id").textValue());
            assertThat(stored.get("body").textValue()).isEqualTo("Investigated the issue.");
        });
    }

    @Test
    void statusChangePersistsIntoLaterTicketDetail() throws Exception {
        String id = createTicket("Lifecycle", "Description", "MEDIUM", null)
                .get("id").textValue();

        JsonNode transitioned = changeStatus(id, "IN_PROGRESS");
        assertThat(transitioned.get("status").textValue()).isEqualTo("IN_PROGRESS");
        assertThat(getTicket(id).get("status").textValue()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void closedTicketRejectsUpdateWithoutChangingPersistedData() throws Exception {
        String id = createTicket("Original title", "Original description", "HIGH", "Alice")
                .get("id").textValue();
        changeStatus(id, "IN_PROGRESS");
        changeStatus(id, "RESOLVED");
        changeStatus(id, "CLOSED");

        MvcResult rejected = mockMvc.perform(patch("/api/tickets/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Forbidden change\"}"))
                .andExpect(status().isConflict())
                .andReturn();
        assertError(rejected, "TERMINAL_TICKET_CONFLICT", 409);

        JsonNode persisted = getTicket(id);
        assertThat(persisted.get("title").textValue()).isEqualTo("Original title");
        assertThat(persisted.get("status").textValue()).isEqualTo("CLOSED");
    }

    @Test
    void cancelledTicketRejectsCommentWithoutPersistingIt() throws Exception {
        String id = createTicket("Cancelled", "Description", "LOW", null)
                .get("id").textValue();
        changeStatus(id, "CANCELLED");

        MvcResult rejected = mockMvc.perform(post("/api/tickets/{id}/comments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Forbidden comment\"}"))
                .andExpect(status().isConflict())
                .andReturn();
        assertError(rejected, "TERMINAL_TICKET_CONFLICT", 409);

        JsonNode persisted = getTicket(id);
        assertThat(persisted.get("status").textValue()).isEqualTo("CANCELLED");
        assertThat(persisted.get("comments")).isEmpty();
    }

    @Test
    void missingTicketErrorsFlowThroughRealStack() throws Exception {
        String missingId = "9223372036854775807";

        assertError(mockMvc.perform(get("/api/tickets/{id}", missingId))
                .andExpect(status().isNotFound()).andReturn(), "TICKET_NOT_FOUND", 404);
        assertError(mockMvc.perform(patch("/api/tickets/{id}", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated\"}"))
                .andExpect(status().isNotFound()).andReturn(), "TICKET_NOT_FOUND", 404);
        assertError(mockMvc.perform(post("/api/tickets/{id}/comments", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Comment\"}"))
                .andExpect(status().isNotFound()).andReturn(), "TICKET_NOT_FOUND", 404);
    }

    @Test
    void representativeValidationFailuresFlowThroughRealStack() throws Exception {
        assertError(mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"missing title\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isBadRequest()).andReturn(), "VALIDATION_ERROR", 400);
        assertError(mockMvc.perform(patch("/api/tickets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()).andReturn(), "VALIDATION_ERROR", 400);
        assertError(mockMvc.perform(patch("/api/tickets/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()).andReturn(), "VALIDATION_ERROR", 400);
        assertError(mockMvc.perform(post("/api/tickets/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()).andReturn(), "VALIDATION_ERROR", 400);
    }

    @Test
    void unknownFieldsAreRejectedByRealJacksonConfiguration() throws Exception {
        assertError(mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Test",
                                  "description": "Test",
                                  "priority": "HIGH",
                                  "unsupportedField": "x"
                                }
                                """))
                .andExpect(status().isBadRequest()).andReturn(), "VALIDATION_ERROR", 400);
        assertError(mockMvc.perform(patch("/api/tickets/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus":"IN_PROGRESS","status":"OPEN"}
                                """))
                .andExpect(status().isBadRequest()).andReturn(), "VALIDATION_ERROR", 400);
    }

    private JsonNode createTicket(
            String title,
            String description,
            String priority,
            String assignee) throws Exception {
        java.util.Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("title", title);
        request.put("description", description);
        request.put("priority", priority);
        if (assignee != null) {
            request.put("assignee", assignee);
        }
        return responseJson(mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private JsonNode getTicket(String id) throws Exception {
        return responseJson(mockMvc.perform(get("/api/tickets/{id}", id))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode changeStatus(String id, String targetStatus) throws Exception {
        return responseJson(mockMvc.perform(patch("/api/tickets/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetStatus", targetStatus))))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void assertError(MvcResult result, String code, int status) throws Exception {
        JsonNode error = responseJson(result);
        assertThat(fieldNames(error))
                .containsExactlyInAnyOrder("code", "message", "status", "path");
        assertThat(error.get("code").textValue()).isEqualTo(code);
        assertThat(error.get("message").textValue()).isNotBlank();
        assertThat(error.get("status").intValue()).isEqualTo(status);
        assertThat(error.get("path").textValue()).isEqualTo(result.getRequest().getRequestURI());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private Set<String> ids(JsonNode array) {
        Set<String> ids = new HashSet<>();
        array.forEach(item -> ids.add(item.get("id").textValue()));
        return ids;
    }

    private Set<String> fieldNames(JsonNode object) {
        Set<String> fields = new HashSet<>();
        object.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
