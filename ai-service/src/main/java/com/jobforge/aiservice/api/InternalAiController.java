package com.jobforge.aiservice.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobforge.aiservice.provider.AIProvider;
import com.jobforge.aiservice.provider.AIProviderSelector;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/ai")
public class InternalAiController {

    private final AIProviderSelector selector;

    public InternalAiController(
            AIProviderSelector selector
    ) {
        this.selector = selector;
    }

    @PostMapping("/job-drafts")
    public AIProvider.ProviderResult jobDraft(
            @RequestBody JsonNode request,
            @RequestHeader(value = "X-Request-Id", required = false)
            String traceId
    ) {

        return generate(
                "JOB_GENERATION",
                request,
                traceId
        );
    }

    @PostMapping("/job-validations")
    public AIProvider.ProviderResult jobValidation(
            @RequestBody JsonNode request,
            @RequestHeader(value = "X-Request-Id", required = false)
            String traceId
    ) {

        return generate(
                "JOB_VALIDATION",
                request,
                traceId
        );
    }

    @PostMapping("/recommendations/jobs")
    public AIProvider.ProviderResult recommendations(
            @RequestBody JsonNode request,
            @RequestHeader(value = "X-Request-Id", required = false)
            String traceId
    ) {

        return generate(
                "JOB_RECOMMENDATION",
                request,
                traceId
        );
    }

    @PostMapping("/career-insights")
    public AIProvider.ProviderResult careerInsights(
            @RequestBody JsonNode request,
            @RequestHeader(value = "X-Request-Id", required = false)
            String traceId
    ) {

        return generate(
                "CAREER_INSIGHT",
                request,
                traceId
        );
    }

    private AIProvider.ProviderResult generate(
            String operation,
            JsonNode request,
            String traceId
    ) {

        return selector
                .select()
                .generate(
                        new AIProvider.ProviderRequest(
                                operation,
                                request.toString(),
                                traceId
                        )
                );
    }
}