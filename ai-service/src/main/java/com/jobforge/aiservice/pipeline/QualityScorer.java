package com.jobforge.aiservice.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Deterministic baseline quality hints for local provider output. */
@Component
public class QualityScorer {
    private final ObjectMapper mapper;
    public QualityScorer(ObjectMapper mapper) { this.mapper = mapper; }
    public JsonNode score(JsonNode draft, AiOutputValidator.Outcome validation) {
        List<Map<String,String>> issues = new ArrayList<>();
        if (draft.path("salary").isMissingNode()) {
            issues.add(Map.of("code","MISSING_SALARY","severity","INFO","field","salary",
                    "message","Salary details were not provided.","suggestion","Add a salary range if available."));
        }
        JsonNode providerIssues = validation.validationResult().path("issues");
        if (providerIssues.isArray()) providerIssues.forEach(node -> issues.add(Map.of(
                "code",node.path("code").asText("VALIDATION_ISSUE"),
                "severity",node.path("severity").asText("WARN"),
                "field",node.path("field").asText("draft"),
                "message",node.path("message").asText("Draft validation issue."),
                "suggestion","Review this field before publishing.")));
        return mapper.valueToTree(Map.of("score",validation.qualityScore()==null?0:validation.qualityScore(),"issues",issues));
    }
}
