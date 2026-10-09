package com.jobforge.backend.shared.error;

/** One entry of {@code error.details[]} (API_CONTRACT §5). */
public record FieldErrorDetail(String field, String code, String message) {}
