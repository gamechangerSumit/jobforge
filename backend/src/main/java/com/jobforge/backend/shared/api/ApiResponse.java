package com.jobforge.backend.shared.api;

/** Success envelope: {@code { "data": ..., "meta": ... }} (API_CONTRACT §4). */
public record ApiResponse<T>(T data, ResponseMeta meta) {}
