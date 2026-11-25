package com.jobforge.aiservice.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobforge.aiservice.domain.AiOperation;

/** Fail-closed output validation SPI. Each generation operation must register a validator before execution. */
public interface AiOutputValidator {
    record Outcome(boolean valid, String schemaVersion, Integer qualityScore, JsonNode validationResult) {}
    AiOperation operation();
    Outcome validate(JsonNode output);
}
