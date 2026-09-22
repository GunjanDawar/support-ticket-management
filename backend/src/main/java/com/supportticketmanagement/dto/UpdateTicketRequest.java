package com.supportticketmanagement.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.supportticketmanagement.persistence.Priority;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public final class UpdateTicketRequest {

    @Size(max = 200, message = "title must not exceed 200 characters")
    private String title;

    @Size(max = 5_000, message = "description must not exceed 5000 characters")
    private String description;

    private Priority priority;
    private String assignee;

    private boolean titlePresent;
    private boolean descriptionPresent;
    private boolean priorityPresent;
    private boolean assigneePresent;

    public String getTitle() {
        return title;
    }

    @JsonSetter
    public void setTitle(String title) {
        this.title = title;
        this.titlePresent = true;
    }

    public String getDescription() {
        return description;
    }

    @JsonSetter
    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    public Priority getPriority() {
        return priority;
    }

    @JsonSetter
    public void setPriority(Priority priority) {
        this.priority = priority;
        this.priorityPresent = true;
    }

    public String getAssignee() {
        return assignee;
    }

    @JsonSetter
    public void setAssignee(String assignee) {
        this.assignee = assignee;
        this.assigneePresent = true;
    }

    @JsonIgnore
    public boolean isTitlePresent() {
        return titlePresent;
    }

    @JsonIgnore
    public boolean isDescriptionPresent() {
        return descriptionPresent;
    }

    @JsonIgnore
    public boolean isPriorityPresent() {
        return priorityPresent;
    }

    @JsonIgnore
    public boolean isAssigneePresent() {
        return assigneePresent;
    }

    @AssertTrue(message = "at least one update field is required")
    @JsonIgnore
    public boolean isAnyFieldPresent() {
        return titlePresent || descriptionPresent || priorityPresent || assigneePresent;
    }

    @AssertTrue(message = "title must not be null when supplied")
    @JsonIgnore
    public boolean isTitleValueValid() {
        return !titlePresent || title != null;
    }

    @AssertTrue(message = "description must not be null when supplied")
    @JsonIgnore
    public boolean isDescriptionValueValid() {
        return !descriptionPresent || description != null;
    }

    @AssertTrue(message = "priority must not be null when supplied")
    @JsonIgnore
    public boolean isPriorityValueValid() {
        return !priorityPresent || priority != null;
    }
}
