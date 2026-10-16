package com.gestaocasas.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** {@code codigo} só vem em 422 de regra do levantamento (RN-xx ou RF-xx): o front mostra "codigo: message". */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(boolean success, String codigo, String message, List<FieldError> errors) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(String message) {
        return new ApiError(false, null, message, null);
    }

    public static ApiError of(String message, List<FieldError> errors) {
        return new ApiError(false, null, message, errors);
    }

    public static ApiError regra(String codigo, String message) {
        return new ApiError(false, codigo, message, null);
    }
}
