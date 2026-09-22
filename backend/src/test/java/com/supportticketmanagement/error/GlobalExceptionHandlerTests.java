package com.supportticketmanagement.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportticketmanagement.dto.CreateTicketRequest;
import com.supportticketmanagement.dto.StatusTransitionRequest;
import com.supportticketmanagement.persistence.Priority;
import com.supportticketmanagement.persistence.TicketStatus;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Autowired
    GlobalExceptionHandlerTests(
            GlobalExceptionHandler handler,
            ObjectMapper objectMapper,
            Validator validator) {
        this.handler = handler;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Test
    void apiErrorResponseSerializesExactlyFourApprovedFieldsWithoutInternalDetails()
            throws Exception {
        ApiErrorResponse response = new ApiErrorResponse(
                ApiErrorCode.VALIDATION_ERROR,
                "The request contains invalid input.",
                400,
                "/api/tickets");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));
        String serialized = json.toString();

        assertThat(fieldNames(json))
                .containsExactlyInAnyOrder("code", "message", "status", "path");
        assertThat(json.get("code").textValue()).isEqualTo("VALIDATION_ERROR");
        assertThat(json.get("status").intValue()).isEqualTo(400);
        assertThat(serialized)
                .doesNotContain(
                        "stackTrace",
                        "exception",
                        "SQLException",
                        "jdbc:",
                        "password",
                        "/home/");
    }

    @Test
    void missingAndExcessiveRequestFieldsMapToValidationError() throws Exception {
        CreateTicketRequest missingTitle =
                new CreateTicketRequest(null, "Description", Priority.LOW, null);
        CreateTicketRequest longTitle =
                new CreateTicketRequest("t".repeat(201), "Description", Priority.LOW, null);

        assertThat(validator.validate(missingTitle)).isNotEmpty();
        assertThat(validator.validate(longTitle)).isNotEmpty();

        assertValidationMapping(methodArgumentNotValid("missing title"), "/api/tickets");
        assertValidationMapping(methodArgumentNotValid("title is too long"), "/api/tickets");
    }

    @Test
    void invalidEnumUnknownPropertyAndMalformedJsonMapToValidationError() {
        assertBindingFailureMapsToValidationError(
                "{\"targetStatus\":\"INVALID\"}",
                StatusTransitionRequest.class,
                "/api/tickets/42/status");
        assertBindingFailureMapsToValidationError(
                "{\"targetStatus\":\"OPEN\",\"status\":\"CLOSED\"}",
                StatusTransitionRequest.class,
                "/api/tickets/42/status");
        assertBindingFailureMapsToValidationError(
                "{\"targetStatus\":",
                StatusTransitionRequest.class,
                "/api/tickets/42/status");
    }

    @Test
    void ticketNotFoundMapsToApproved404ResponseAndPath() {
        ResponseEntity<ApiErrorResponse> response = handler.handleTicketNotFound(
                new TicketNotFoundException(42L),
                request("/api/tickets/42", "ignored=true"));

        assertMapping(response, HttpStatus.NOT_FOUND, ApiErrorCode.TICKET_NOT_FOUND);
        assertThat(response.getBody().path()).isEqualTo("/api/tickets/42");
    }

    @Test
    void invalidTransitionMapsToDistinctApproved409Response() {
        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidStatusTransition(
                new InvalidStatusTransitionException(TicketStatus.OPEN, TicketStatus.CLOSED),
                request("/api/tickets/42/status", null));

        assertMapping(
                response,
                HttpStatus.CONFLICT,
                ApiErrorCode.INVALID_STATUS_TRANSITION);
        assertThat(response.getBody().path()).isEqualTo("/api/tickets/42/status");
    }

    @Test
    void terminalConflictMapsToDistinctApproved409Response() {
        ResponseEntity<ApiErrorResponse> response = handler.handleTerminalTicketConflict(
                new TerminalTicketConflictException(42L, TicketStatus.CLOSED),
                request("/api/tickets/42/comments", null));

        assertMapping(
                response,
                HttpStatus.CONFLICT,
                ApiErrorCode.TERMINAL_TICKET_CONFLICT);
        assertThat(response.getBody().path()).isEqualTo("/api/tickets/42/comments");
    }

    @Test
    void invalidEnumAndInvalidTransitionRemain400And409Respectively() {
        HttpMessageNotReadableException invalidEnum = unreadable(
                "{\"targetStatus\":\"INVALID\"}", StatusTransitionRequest.class);
        ResponseEntity<ApiErrorResponse> syntaxResponse = handler.handleValidationFailure(
                invalidEnum,
                request("/api/tickets/42/status", null));
        ResponseEntity<ApiErrorResponse> transitionResponse =
                handler.handleInvalidStatusTransition(
                        new InvalidStatusTransitionException(
                                TicketStatus.OPEN, TicketStatus.CLOSED),
                        request("/api/tickets/42/status", null));

        assertMapping(
                syntaxResponse,
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_ERROR);
        assertMapping(
                transitionResponse,
                HttpStatus.CONFLICT,
                ApiErrorCode.INVALID_STATUS_TRANSITION);
    }

    private void assertBindingFailureMapsToValidationError(
            String json,
            Class<?> requestType,
            String path) {
        assertValidationMapping(unreadable(json, requestType), path);
    }

    private HttpMessageNotReadableException unreadable(String json, Class<?> requestType) {
        assertThatThrownBy(() -> objectMapper.readValue(json, requestType))
                .isInstanceOf(JacksonException.class);

        try {
            objectMapper.readValue(json, requestType);
            throw new AssertionError("Expected JSON deserialization to fail");
        } catch (JacksonException exception) {
            return new HttpMessageNotReadableException(
                    "Invalid request body",
                    exception,
                    new MockHttpInputMessage(json.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private MethodArgumentNotValidException methodArgumentNotValid(String message)
            throws Exception {
        Method method = GlobalExceptionHandlerTests.class.getDeclaredMethod(
                "validationTarget", CreateTicketRequest.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new ObjectError("request", message));
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    @SuppressWarnings("unused")
    private void validationTarget(CreateTicketRequest request) {
    }

    private void assertValidationMapping(Exception exception, String path) {
        ResponseEntity<ApiErrorResponse> response = handler.handleValidationFailure(
                exception,
                request(path, null));

        assertMapping(response, HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR);
        assertThat(response.getBody().path()).isEqualTo(path);
    }

    private void assertMapping(
            ResponseEntity<ApiErrorResponse> response,
            HttpStatus expectedStatus,
            ApiErrorCode expectedCode) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(expectedStatus.value());
        assertThat(response.getBody().code()).isEqualTo(expectedCode);
    }

    private MockHttpServletRequest request(String path, String query) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setQueryString(query);
        return request;
    }

    private Set<String> fieldNames(JsonNode json) {
        Set<String> fields = new HashSet<>();
        json.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
