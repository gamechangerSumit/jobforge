package com.jobforge.aiservice.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LocalTemplateProvider implements AIProvider {

    private final ObjectMapper objectMapper;

    public LocalTemplateProvider(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderInfo info() {
        return new ProviderInfo(
                "local-template",
                "template-v1"
        );
    }

    @Override
    public ProviderResult generate(
            ProviderRequest request
    ) {

        try {
            JsonNode input =
                    objectMapper.readTree(request.input());

            return switch (request.operation()) {

                case "JOB_GENERATION" ->
                        generateJob(input);

                case "JOB_VALIDATION" ->
                        validateJob(input);

                case "JOB_RECOMMENDATION" ->
                        recommendJobs(input);

                case "CAREER_INSIGHT" ->
                        careerInsights(input);

                default ->
                        throw new IllegalArgumentException(
                                "Unsupported AI operation: "
                                        + request.operation()
                        );
            };

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Local AI provider failed",
                    ex
            );
        }
    }

    private ProviderResult generateJob(
            JsonNode input
    ) throws Exception {

        String title =
                input.path("title")
                        .asText("Software Engineer");

        List<String> skills =
                new ArrayList<>();

        input.path("skills")
                .forEach(node ->
                        skills.add(node.asText())
                );

        StringBuilder description =
                new StringBuilder();

        description
                .append("We are looking for a ")
                .append(title)
                .append(" to join our engineering team.");

        String output =
                objectMapper.writeValueAsString(
                        java.util.Map.ofEntries(

                                java.util.Map.entry(
                                        "title",
                                        title
                                ),

                                java.util.Map.entry(
                                        "summary",
                                        "Join our team as a "
                                                + title
                                                + " and build reliable "
                                                + "software products."
                                ),

                                java.util.Map.entry(
                                        "responsibilities",
                                        List.of(
                                                "Design and implement software features.",
                                                "Collaborate with engineering teams.",
                                                "Write maintainable and tested code."
                                        )
                                ),

                                java.util.Map.entry(
                                        "requirements",
                                        List.of(
                                                "Strong programming fundamentals.",
                                                "Experience with software development.",
                                                "Ability to work collaboratively."
                                        )
                                ),

                                java.util.Map.entry(
                                        "niceToHave",
                                        List.of(
                                                "Cloud experience",
                                                "CI/CD experience"
                                        )
                                ),

                                java.util.Map.entry(
                                        "benefits",
                                        List.of(
                                                "Learning opportunities",
                                                "Collaborative engineering environment"
                                        )
                                ),

                                java.util.Map.entry(
                                        "skills",
                                        skills.stream()
                                                .map(skill ->
                                                        java.util.Map.of(
                                                                "name",
                                                                skill,
                                                                "required",
                                                                true
                                                        )
                                                )
                                                .toList()
                                ),

                                java.util.Map.entry(
                                        "employmentType",
                                        input.path("employmentType")
                                                .asText("FULL_TIME")
                                ),

                                java.util.Map.entry(
                                        "workMode",
                                        input.path("workMode")
                                                .asText("HYBRID")
                                ),

                                java.util.Map.entry(
                                        "experienceLevel",
                                        input.path("experienceLevel")
                                                .asText("MID")
                                ),

                                java.util.Map.entry(
                                        "descriptionMarkdown",
                                        description.toString()
                                )
                        )
                );

        return new ProviderResult(
                output,
                0,
                0
        );
    }

    private ProviderResult validateJob(
            JsonNode input
    ) throws Exception {

        String title =
                input.path("title")
                        .asText("");

        List<Object> issues =
                new ArrayList<>();

        if (title.isBlank()) {
            issues.add(
                    java.util.Map.of(
                            "code",
                            "VAGUE_TITLE",

                            "severity",
                            "ERROR",

                            "field",
                            "title",

                            "message",
                            "A job title is required.",

                            "suggestion",
                            "Provide a specific job title."
                    )
            );
        }

        if (title.length() < 3) {
            issues.add(
                    java.util.Map.of(
                            "code",
                            "TOO_SHORT",

                            "severity",
                            "ERROR",

                            "field",
                            "title",

                            "message",
                            "The job title is too short.",

                            "suggestion",
                            "Use a descriptive title."
                    )
            );
        }

        int score =
                issues.isEmpty()
                        ? 90
                        : 60;

        String output =
                objectMapper.writeValueAsString(
                        java.util.Map.of(
                                "score",
                                score,

                                "issues",
                                issues
                        )
                );

        return new ProviderResult(
                output,
                0,
                0
        );
    }

    private ProviderResult recommendJobs(
            JsonNode input
    ) throws Exception {

        return new ProviderResult(
                objectMapper.writeValueAsString(
                        java.util.Map.of(
                                "recommendations",
                                List.of()
                        )
                ),
                0,
                0
        );
    }

    private ProviderResult careerInsights(
            JsonNode input
    ) throws Exception {

        String output =
                objectMapper.writeValueAsString(
                        java.util.Map.of(
                                "profileStrength",
                                java.util.Map.of(
                                        "score",
                                        50,

                                        "suggestions",
                                        List.of(
                                                "Add a professional summary.",
                                                "Add more relevant skills."
                                        )
                                ),

                                "skillGaps",
                                List.of(),

                                "marketSnapshot",
                                java.util.Map.of(
                                        "topSkills",
                                        List.of()
                                ),

                                "nextSteps",
                                List.of(
                                        "Complete your profile.",
                                        "Add measurable experience."
                                )
                        )
                );

        return new ProviderResult(
                output,
                0,
                0
        );
    }
}