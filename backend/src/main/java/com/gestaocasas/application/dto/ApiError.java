package com.gestaocasas.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(boolean success, String message, List<FieldError> errors) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(String message) {
        return new ApiError(false, message, null);
    }

    public static ApiError of(String message, List<FieldError> errors) {
        return new ApiError(false, message, errors);
    }
}
