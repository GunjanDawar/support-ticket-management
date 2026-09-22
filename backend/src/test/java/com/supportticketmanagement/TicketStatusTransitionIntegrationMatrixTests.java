package com.supportticketmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportticketmanagement.persistence.Priority;
import com.supportticketmanagement.persistence.Ticket;
import com.supportticketmanagement.persistence.TicketStatus;
import com.supportticketmanagement.repository.CommentRepository;
import com.supportticketmanagement.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
class TicketStatusTransitionIntegrationMatrixTests {

    private static final String TITLE = "Matrix ticket";
    private static final String DESCRIPTION = "State-machine integration description";
    private static final Priority PRIORITY = Priority.HIGH;
    private static final String ASSIGNEE = "Matrix assignee";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;

    @Autowired
    TicketStatusTransitionIntegrationMatrixTests(
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

    @ParameterizedTest(name = "{0} to {1}: HTTP {3}")
    @MethodSource("completeTransitionMatrix")
    void enforcesCompleteMatrixThroughRestAndDatabase(
            TicketStatus source,
            TicketStatus target,
            boolean allowed,
            int expectedHttpStatus) throws Exception {
        Ticket ticket = persistTicketInSourceState(source);
        String id = ticket.getId().toString();

        MvcResult transition = mockMvc.perform(patch("/api/tickets/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("targetStatus", target.name()))))
                .andExpect(status().is(expectedHttpStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode transitionBody = responseJson(transition);
        if (allowed) {
            assertTicketFields(transitionBody, id, target);
        } else {
            assertInvalidTransitionError(transitionBody, transition);
        }

        JsonNode persisted = responseJson(mockMvc.perform(get("/api/tickets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn());
        assertTicketFields(persisted, id, allowed ? target : source);
    }

    private Ticket persistTicketInSourceState(TicketStatus source) {
        Ticket ticket = new Ticket(TITLE, DESCRIPTION, PRIORITY, ASSIGNEE);
        ticket.setStatus(source);
        return ticketRepository.save(ticket);
    }

    private void assertTicketFields(JsonNode ticket, String id, TicketStatus status) {
        assertThat(ticket.get("id").textValue()).isEqualTo(id);
        assertThat(ticket.get("title").textValue()).isEqualTo(TITLE);
        assertThat(ticket.get("description").textValue()).isEqualTo(DESCRIPTION);
        assertThat(ticket.get("priority").textValue()).isEqualTo(PRIORITY.name());
        assertThat(ticket.get("assignee").textValue()).isEqualTo(ASSIGNEE);
        assertThat(ticket.get("status").textValue()).isEqualTo(status.name());
    }

    private void assertInvalidTransitionError(JsonNode error, MvcResult result) {
        assertThat(fieldNames(error))
                .containsExactlyInAnyOrder("code", "message", "status", "path");
        assertThat(error.get("code").textValue()).isEqualTo("INVALID_STATUS_TRANSITION");
        assertThat(error.get("message").textValue()).isNotBlank();
        assertThat(error.get("status").intValue()).isEqualTo(409);
        assertThat(error.get("path").textValue())
                .isEqualTo(result.getRequest().getRequestURI());
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private Set<String> fieldNames(JsonNode object) {
        Set<String> fields = new HashSet<>();
        object.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private static Stream<Arguments> completeTransitionMatrix() {
        return Stream.of(
                arguments(TicketStatus.OPEN, TicketStatus.OPEN, false, 409),
                arguments(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, true, 200),
                arguments(TicketStatus.OPEN, TicketStatus.RESOLVED, false, 409),
                arguments(TicketStatus.OPEN, TicketStatus.CLOSED, false, 409),
                arguments(TicketStatus.OPEN, TicketStatus.CANCELLED, true, 200),

                arguments(TicketStatus.IN_PROGRESS, TicketStatus.OPEN, false, 409),
                arguments(TicketStatus.IN_PROGRESS, TicketStatus.IN_PROGRESS, false, 409),
                arguments(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, true, 200),
                arguments(TicketStatus.IN_PROGRESS, TicketStatus.CLOSED, false, 409),
                arguments(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED, true, 200),

                arguments(TicketStatus.RESOLVED, TicketStatus.OPEN, false, 409),
                arguments(TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS, false, 409),
                arguments(TicketStatus.RESOLVED, TicketStatus.RESOLVED, false, 409),
                arguments(TicketStatus.RESOLVED, TicketStatus.CLOSED, true, 200),
                arguments(TicketStatus.RESOLVED, TicketStatus.CANCELLED, false, 409),

                arguments(TicketStatus.CLOSED, TicketStatus.OPEN, false, 409),
                arguments(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS, false, 409),
                arguments(TicketStatus.CLOSED, TicketStatus.RESOLVED, false, 409),
                arguments(TicketStatus.CLOSED, TicketStatus.CLOSED, false, 409),
                arguments(TicketStatus.CLOSED, TicketStatus.CANCELLED, false, 409),

                arguments(TicketStatus.CANCELLED, TicketStatus.OPEN, false, 409),
                arguments(TicketStatus.CANCELLED, TicketStatus.IN_PROGRESS, false, 409),
                arguments(TicketStatus.CANCELLED, TicketStatus.RESOLVED, false, 409),
                arguments(TicketStatus.CANCELLED, TicketStatus.CLOSED, false, 409),
                arguments(TicketStatus.CANCELLED, TicketStatus.CANCELLED, false, 409));
    }
}
