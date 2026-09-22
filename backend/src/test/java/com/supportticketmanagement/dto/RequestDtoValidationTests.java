package com.supportticketmanagement.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportticketmanagement.persistence.Priority;
import com.supportticketmanagement.persistence.TicketStatus;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class RequestDtoValidationTests {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Autowired
    RequestDtoValidationTests(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Test
    void createRequestAcceptsApprovedPrioritiesOptionalAssigneeAndWhitespaceTitle()
            throws Exception {
        for (Priority priority : Priority.values()) {
            CreateTicketRequest request = read(
                    """
                    {
                      "title": "   ",
                      "description": "Description",
                      "priority": "%s"
                    }
                    """.formatted(priority),
                    CreateTicketRequest.class);
            assertValid(request);
            assertThat(request.assignee()).isNull();
        }

        CreateTicketRequest assigned = read(
                """
                {
                  "title": "Title",
                  "description": "Description",
                  "priority": "HIGH",
                  "assignee": "Support Team"
                }
                """,
                CreateTicketRequest.class);
        assertValid(assigned);
        assertThat(assigned.assignee()).isEqualTo("Support Team");
    }

    @Test
    void createRequestRejectsMissingOrNullRequiredFields() throws Exception {
        assertInvalid(read(
                """
                {"description": "Description", "priority": "LOW"}
                """,
                CreateTicketRequest.class));
        assertInvalid(read(
                """
                {"title": "Title", "priority": "LOW"}
                """,
                CreateTicketRequest.class));
        assertInvalid(read(
                """
                {"title": "Title", "description": "Description"}
                """,
                CreateTicketRequest.class));
        assertInvalid(read(
                """
                {"title": null, "description": null, "priority": null}
                """,
                CreateTicketRequest.class));
    }

    @Test
    void createRequestEnforcesOnlyApprovedMaximumLengths() throws Exception {
        CreateTicketRequest titleTooLong = new CreateTicketRequest(
                "t".repeat(201), "Description", Priority.LOW, null);
        CreateTicketRequest descriptionTooLong = new CreateTicketRequest(
                "Title", "d".repeat(5_001), Priority.LOW, null);

        assertInvalid(titleTooLong);
        assertInvalid(descriptionTooLong);
        assertValid(new CreateTicketRequest(
                "t".repeat(200), "d".repeat(5_000), Priority.LOW, null));
    }

    @Test
    void createRequestRejectsUnsupportedPriorityDuringDeserialization() {
        assertDeserializationRejected(
                """
                {
                  "title": "Title",
                  "description": "Description",
                  "priority": "URGENT"
                }
                """,
                CreateTicketRequest.class);
    }

    @Test
    void createRequestRejectsProhibitedAndArbitraryUnknownFields() {
        assertDeserializationRejected(
                """
                {
                  "title": "Title",
                  "description": "Description",
                  "priority": "HIGH",
                  "status": "CLOSED"
                }
                """,
                CreateTicketRequest.class);
        assertDeserializationRejected(
                """
                {
                  "title": "Title",
                  "description": "Description",
                  "priority": "HIGH",
                  "unknownField": "value"
                }
                """,
                CreateTicketRequest.class);
    }

    @Test
    void updateRequestAcceptsEachApprovedFieldAndMultipleFields() throws Exception {
        for (String json : Arrays.asList(
                """
                {"title": "New title"}
                """,
                """
                {"description": "New description"}
                """,
                """
                {"priority": "CRITICAL"}
                """,
                """
                {"assignee": "Support Team"}
                """,
                """
                {
                  "title": "New title",
                  "description": "New description",
                  "priority": "HIGH",
                  "assignee": "Support Team"
                }
                """)) {
            assertValid(read(json, UpdateTicketRequest.class));
        }
    }

    @Test
    void updateRequestPreservesOmittedAndExplicitNullAssignee() throws Exception {
        UpdateTicketRequest omitted = read(
                """
                {"title": "New title"}
                """,
                UpdateTicketRequest.class);
        UpdateTicketRequest explicitNull = read(
                """
                {"assignee": null}
                """,
                UpdateTicketRequest.class);

        assertThat(omitted.isAssigneePresent()).isFalse();
        assertThat(explicitNull.isAssigneePresent()).isTrue();
        assertThat(explicitNull.getAssignee()).isNull();
        assertValid(omitted);
        assertValid(explicitNull);
    }

    @Test
    void updateRequestRejectsEmptyObjectAndNullRequiredUpdateValues() throws Exception {
        assertInvalid(read("{}", UpdateTicketRequest.class));
        assertInvalid(read("{\"title\": null}", UpdateTicketRequest.class));
        assertInvalid(read("{\"description\": null}", UpdateTicketRequest.class));
        assertInvalid(read("{\"priority\": null}", UpdateTicketRequest.class));
    }

    @Test
    void updateRequestRejectsExcessiveLengthsAndUnsupportedPriority() throws Exception {
        assertInvalid(read(
                "{\"title\":\"%s\"}".formatted("t".repeat(201)),
                UpdateTicketRequest.class));
        assertInvalid(read(
                "{\"description\":\"%s\"}".formatted("d".repeat(5_001)),
                UpdateTicketRequest.class));
        assertDeserializationRejected(
                """
                {"priority": "URGENT"}
                """,
                UpdateTicketRequest.class);
    }

    @Test
    void updateRequestRejectsStatusAndOtherUnknownFields() {
        assertDeserializationRejected(
                """
                {"status": "CLOSED"}
                """,
                UpdateTicketRequest.class);
        assertDeserializationRejected(
                """
                {"title": "New title", "foo": "bar"}
                """,
                UpdateTicketRequest.class);
    }

    @Test
    void statusRequestValidatesShapeAndApprovedEnumValues() throws Exception {
        for (TicketStatus status : TicketStatus.values()) {
            StatusTransitionRequest request = read(
                    "{\"targetStatus\":\"%s\"}".formatted(status),
                    StatusTransitionRequest.class);
            assertValid(request);
        }

        assertInvalid(read("{}", StatusTransitionRequest.class));
        assertInvalid(read("{\"targetStatus\":null}", StatusTransitionRequest.class));
        assertDeserializationRejected(
                """
                {"targetStatus": "UNKNOWN"}
                """,
                StatusTransitionRequest.class);
        assertDeserializationRejected(
                """
                {"status": "IN_PROGRESS"}
                """,
                StatusTransitionRequest.class);
        assertDeserializationRejected(
                """
                {"targetStatus": "IN_PROGRESS", "status": "CLOSED"}
                """,
                StatusTransitionRequest.class);
    }

    @Test
    void commentRequestRequiresOnlyNonNullBodyWithoutLengthOrBlankRestriction()
            throws Exception {
        assertValid(read("{\"body\":\"Comment\"}", AddCommentRequest.class));
        assertValid(read("{\"body\":\"   \"}", AddCommentRequest.class));
        assertValid(new AddCommentRequest("x".repeat(20_000)));
        assertInvalid(read("{}", AddCommentRequest.class));
        assertInvalid(read("{\"body\":null}", AddCommentRequest.class));
        assertDeserializationRejected(
                """
                {"body": "Comment", "timestamp": "2026-09-22T17:00:00Z"}
                """,
                AddCommentRequest.class);
    }

    private <T> T read(String json, Class<T> requestType) throws Exception {
        return objectMapper.readValue(json, requestType);
    }

    private void assertValid(Object request) {
        assertThat(validator.validate(request)).isEmpty();
    }

    private void assertInvalid(Object request) {
        assertThat(validator.validate(request)).isNotEmpty();
    }

    private void assertDeserializationRejected(String json, Class<?> requestType) {
        assertThatThrownBy(() -> objectMapper.readValue(json, requestType))
                .isInstanceOfAny(com.fasterxml.jackson.core.JacksonException.class);
    }
}
