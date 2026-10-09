package com.jobforge.backend.shared.api;

/** Error envelope: {@code { "error": {...} }} (API_CONTRACT §5, D-15). */
public record ErrorEnvelope(ErrorBody error) {}
