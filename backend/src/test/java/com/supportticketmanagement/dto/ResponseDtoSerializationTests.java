package com.supportticketmanagement.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportticketmanagement.persistence.Priority;
import com.supportticketmanagement.persistence.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

@JsonTest
class ResponseDtoSerializationTests {

    private final ObjectMapper objectMapper;

    @Autowired
    ResponseDtoSerializationTests(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Test
    void serializesTicketResourceWithExactlyApprovedFields() throws Exception {
        JsonNode json = json(new TicketResponse(
                42L,
                "Example ticket",
                "Example description",
                Priority.HIGH,
                "alice",
                TicketStatus.OPEN));

        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "id", "title", "description", "priority", "assignee", "status");
    }

    @Test
    void serializesTicketResourceIdAsString() throws Exception {
        JsonNode json = json(new TicketResponse(
                42L,
                "Example ticket",
                "Example description",
                Priority.HIGH,
                "alice",
                TicketStatus.OPEN));

        assertThat(json.get("id").isTextual()).isTrue();
        assertThat(json.get("id").textValue()).isEqualTo("42");
    }

    @Test
    void serializesMaximumLongTicketResourceIdAsString() throws Exception {
        JsonNode json = json(new TicketResponse(
                Long.MAX_VALUE,
                "Example ticket",
                "Example description",
                Priority.HIGH,
                "alice",
                TicketStatus.OPEN));

        assertThat(json.get("id").isTextual()).isTrue();
        assertThat(json.get("id").textValue()).isEqualTo("9223372036854775807");
    }

    @Test
    void serializesTicketResourceEnumsUsingApprovedNames() throws Exception {
        JsonNode json = json(new TicketResponse(
                42L,
                "Example ticket",
                "Example description",
                Priority.HIGH,
                "alice",
                TicketStatus.OPEN));

        assertThat(json.get("priority").textValue()).isEqualTo("HIGH");
        assertThat(json.get("status").textValue()).isEqualTo("OPEN");
    }

    @Test
    void serializesTicketResourceNullAssigneeAsJsonNull() throws Exception {
        JsonNode json = json(new TicketResponse(
                42L,
                "Example ticket",
                "Example description",
                Priority.HIGH,
                null,
                TicketStatus.OPEN));

        assertThat(json.has("assignee")).isTrue();
        assertThat(json.get("assignee").isNull()).isTrue();
    }

    @Test
    void serializesCompleteRepresentativeTicketResource() throws Exception {
        JsonNode json = json(new TicketResponse(
                42L,
                "Example ticket",
                "Example description",
                Priority.HIGH,
                "alice",
                TicketStatus.OPEN));
        JsonNode expected = objectMapper.readTree(
                """
                {
                  "id": "42",
                  "title": "Example ticket",
                  "description": "Example description",
                  "priority": "HIGH",
                  "assignee": "alice",
                  "status": "OPEN"
                }
                """);

        assertThat(json).isEqualTo(expected);
    }

    @Test
    void serializesTicketSummaryWithExactlyApprovedFields() throws Exception {
        TicketSummaryResponse response = new TicketSummaryResponse(
                42L, "Example", Priority.HIGH, "john", TicketStatus.OPEN);

        JsonNode json = json(response);

        assertThat(fieldNames(json))
                .containsExactlyInAnyOrder("id", "title", "priority", "assignee", "status");
        assertThat(json.get("id").isTextual()).isTrue();
        assertThat(json.get("id").textValue()).isEqualTo("42");
        assertThat(json.get("title").textValue()).isEqualTo("Example");
        assertThat(json.get("priority").textValue()).isEqualTo("HIGH");
        assertThat(json.get("assignee").textValue()).isEqualTo("john");
        assertThat(json.get("status").textValue()).isEqualTo("OPEN");
        assertThat(json.has("description")).isFalse();
        assertThat(json.has("comments")).isFalse();
    }

    @Test
    void serializesNullAssigneeWithoutAddingSummaryFields() throws Exception {
        JsonNode json = json(new TicketSummaryResponse(
                1L, "Unassigned", Priority.LOW, null, TicketStatus.IN_PROGRESS));

        assertThat(json.has("assignee")).isTrue();
        assertThat(json.get("assignee").isNull()).isTrue();
        assertThat(fieldNames(json))
                .containsExactlyInAnyOrder("id", "title", "priority", "assignee", "status");
    }

    @Test
    void serializesTicketDetailAndNestedCommentWithExactlyApprovedFields()
            throws Exception {
        CommentResponse comment = new CommentResponse(
                84L,
                "The issue has been reproduced.",
                Instant.parse("2026-09-22T10:15:30Z"));
        TicketDetailResponse response = new TicketDetailResponse(
                42L,
                "Example",
                "Detailed description",
                Priority.CRITICAL,
                null,
                TicketStatus.RESOLVED,
                List.of(comment));

        JsonNode json = json(response);
        JsonNode commentJson = json.get("comments").get(0);

        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "id", "title", "description", "priority", "assignee", "status", "comments");
        assertThat(json.get("id").isTextual()).isTrue();
        assertThat(json.get("description").textValue()).isEqualTo("Detailed description");
        assertThat(json.get("comments").isArray()).isTrue();
        assertThat(fieldNames(commentJson)).containsExactlyInAnyOrder("id", "body", "timestamp");
        assertThat(commentJson.get("id").textValue()).isEqualTo("84");
        assertThat(commentJson.has("ticketId")).isFalse();
    }

    @Test
    void serializesCommentTimestampAsRfc3339UtcInstant() throws Exception {
        JsonNode json = json(new CommentResponse(
                84L,
                "Comment body",
                Instant.parse("2026-09-22T10:15:30Z")));

        assertThat(fieldNames(json)).containsExactlyInAnyOrder("id", "body", "timestamp");
        assertThat(json.get("id").isTextual()).isTrue();
        assertThat(json.get("body").textValue()).isEqualTo("Comment body");
        assertThat(json.get("timestamp").textValue()).isEqualTo("2026-09-22T10:15:30Z");
        assertThat(json.has("ticketId")).isFalse();
        assertThat(json.has("author")).isFalse();
        assertThat(json.has("createdAt")).isFalse();
        assertThat(json.has("updatedAt")).isFalse();
    }

    @Test
    void serializesRepresentativeLongIdsAsExactDecimalStrings() throws Exception {
        for (long id : new long[] {1L, 42L, Long.MAX_VALUE}) {
            JsonNode json = json(new TicketSummaryResponse(
                    id, "Ticket", Priority.MEDIUM, null, TicketStatus.CLOSED));

            assertThat(json.get("id").isTextual()).isTrue();
            assertThat(json.get("id").textValue()).isEqualTo(Long.toString(id));
        }
    }

    @Test
    void rejectsNullIdsInsteadOfCreatingFallbackApiValues() {
        assertThatNullPointerException().isThrownBy(() -> new TicketSummaryResponse(
                null, "Ticket", Priority.LOW, null, TicketStatus.OPEN));
        assertThatNullPointerException().isThrownBy(() -> new CommentResponse(
                null, "Body", Instant.parse("2026-09-22T10:15:30Z")));
        assertThatNullPointerException().isThrownBy(() -> new TicketDetailResponse(
                null,
                "Ticket",
                "Description",
                Priority.LOW,
                null,
                TicketStatus.OPEN,
                List.of()));
    }

    private JsonNode json(Object value) throws Exception {
        return objectMapper.readTree(objectMapper.writeValueAsBytes(value));
    }

    private Set<String> fieldNames(JsonNode json) {
        Set<String> fields = new HashSet<>();
        json.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
