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

                case "CANDIDATE_RECOMMENDATION" ->
                        recommendCandidates(input);

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
                .forEach(node -> {
                    if (node.isTextual() && !node.asText().isBlank() && !skills.contains(node.asText())) {
                        skills.add(node.asText().trim());
                    }
                });

        // API_CONTRACT §14.1 requires at least three draft skills even when the recruiter
        // supplies only one or two seed skills. Clearly generic additions remain editable.
        List<String> defaults = List.of("Communication", "Problem solving", "Team collaboration");
        for (String fallback : defaults) {
            if (skills.size() >= 3) break;
            if (!skills.contains(fallback)) skills.add(fallback);
        }
        if (skills.size() > 20) skills.subList(20, skills.size()).clear();

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

    private ProviderResult recommendJobs(JsonNode input) throws Exception {
        JsonNode context = input.path("context");
        JsonNode profile = context.path("profile");
        JsonNode jobs = context.path("jobs");
        java.util.Set<String> candidateSkills = normalizedSkills(profile.path("skills"));
        List<java.util.Map<String, Object>> recommendations = new ArrayList<>();
        if (jobs.isArray()) {
            jobs.forEach(job -> {
                List<String> required = textValues(job.path("skills"));
                List<String> matched = required.stream()
                        .filter(skill -> candidateSkills.contains(normalize(skill))).distinct().toList();
                List<String> missing = required.stream()
                        .filter(skill -> !candidateSkills.contains(normalize(skill))).distinct().toList();
                int score = required.isEmpty() ? 35
                        : (int) Math.round(35 + 65.0 * matched.size() / required.size());
                String title = job.path("title").asText("Open role");
                String company = job.path("companyName").asText("");
                String reason = matched.isEmpty()
                        ? "This role is in your searchable location; compare its requirements with your current skills."
                        : "Your profile matches " + matched.size() + " listed skill"
                                + (matched.size() == 1 ? "" : "s") + ": " + String.join(", ", matched) + ".";
                java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("jobId", job.path("id").asText(""));
                item.put("title", title);
                item.put("companyName", company);
                item.put("matchScore", score);
                item.put("matchedSkills", matched);
                item.put("missingSkills", missing);
                item.put("reason", reason);
                recommendations.add(item);
            });
        }
        recommendations.sort((a, b) -> Integer.compare((Integer) b.get("matchScore"), (Integer) a.get("matchScore")));
        String output = objectMapper.writeValueAsString(java.util.Map.of(
                "recommendations", recommendations.stream().limit(10).toList()));
        return new ProviderResult(output, 0, 0);
    }

    private ProviderResult recommendCandidates(JsonNode input) throws Exception {
        JsonNode context = input.path("context");
        java.util.Set<String> requiredSkills = normalizedSkills(context.path("job").path("skills"));
        List<java.util.Map<String, Object>> ranked = new ArrayList<>();
        JsonNode candidates = context.path("candidates");
        if (candidates.isArray()) {
            candidates.forEach(candidate -> {
                List<String> skills = textValues(candidate.path("skills"));
                List<String> matched = skills.stream().filter(skill -> requiredSkills.contains(normalize(skill)))
                        .distinct().toList();
                List<String> missing = requiredSkills.stream().filter(skill ->
                        skills.stream().noneMatch(value -> normalize(value).equals(skill))).toList();
                int score = requiredSkills.isEmpty() ? 35
                        : (int) Math.round(35 + 65.0 * matched.size() / requiredSkills.size());
                java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("profileId", candidate.path("profileId").asText(""));
                item.put("headline", candidate.path("headline").asText(""));
                item.put("currentTitle", candidate.path("currentTitle").asText(""));
                item.put("matchScore", score);
                item.put("matchedSkills", matched);
                item.put("missingSkills", missing);
                item.put("reason", matched.isEmpty()
                        ? "Review this applicant's experience and profile manually against the role."
                        : "Applicant profile lists " + String.join(", ", matched) + " from the role's skills.");
                ranked.add(item);
            });
        }
        ranked.sort((a, b) -> Integer.compare((Integer) b.get("matchScore"), (Integer) a.get("matchScore")));
        return new ProviderResult(objectMapper.writeValueAsString(java.util.Map.of(
                "candidates", ranked.stream().limit(50).toList())), 0, 0);
    }

    private ProviderResult careerInsights(JsonNode input) throws Exception {
        JsonNode context = input.path("context");
        JsonNode profile = context.path("profile");
        JsonNode jobs = context.path("jobs");
        java.util.Set<String> candidateSkills = normalizedSkills(profile.path("skills"));
        List<String> suggestions = new ArrayList<>();
        if (profile.path("headline").asText("").isBlank()) suggestions.add("Add a clear professional headline.");
        if (profile.path("summary").asText("").trim().length() < 50) suggestions.add("Write a 50+ character professional summary with measurable outcomes.");
        if (profile.path("currentTitle").asText("").isBlank()) suggestions.add("Add your current or most recent job title.");
        if (profile.path("location").path("city").asText("").isBlank()) suggestions.add("Add your preferred city to improve local job matching.");
        if (candidateSkills.size() < 3) suggestions.add("Add at least three relevant skills and set their proficiency levels.");
        if (profile.path("yearsExperience").isMissingNode() || profile.path("yearsExperience").isNull()) {
            suggestions.add("Add your years of experience so employers can assess role fit.");
        }

        java.util.Map<String, Integer> demand = new java.util.HashMap<>();
        if (jobs.isArray()) jobs.forEach(job -> textValues(job.path("skills")).forEach(skill ->
                demand.merge(skill, 1, Integer::sum)));
        List<java.util.Map<String, Object>> topSkills = demand.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(java.util.Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .limit(5)
                .map(entry -> java.util.Map.<String, Object>of("skill", entry.getKey(), "jobCount", entry.getValue()))
                .toList();
        List<java.util.Map<String, Object>> gaps = demand.entrySet().stream()
                .filter(entry -> !candidateSkills.contains(normalize(entry.getKey())))
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> java.util.Map.<String, Object>of("skill", entry.getKey(), "jobsMentioningSkill", entry.getValue()))
                .toList();
        int completeness = profile.path("profileCompleteness").asInt(0);
        int strength = Math.max(0, Math.min(100, completeness));
        List<String> nextSteps = new ArrayList<>(suggestions);
        if (!gaps.isEmpty()) nextSteps.add("Prioritize one in-demand skill from the gap list and add a practical project to demonstrate it.");
        if (nextSteps.isEmpty()) nextSteps.add("Keep your profile current and tailor each application to the role's required skills.");
        String output = objectMapper.writeValueAsString(java.util.Map.of(
                "profileStrength", java.util.Map.of("score", strength, "suggestions", suggestions),
                "skillGaps", gaps,
                "marketSnapshot", java.util.Map.of("topSkills", topSkills, "sampledJobs", jobs.isArray() ? jobs.size() : 0),
                "nextSteps", nextSteps));
        return new ProviderResult(output, 0, 0);
    }

    private static java.util.Set<String> normalizedSkills(JsonNode node) {
        java.util.Set<String> values = new java.util.HashSet<>();
        textValues(node).forEach(value -> values.add(normalize(value)));
        return values;
    }

    private static List<String> textValues(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) values.add(value.asText().trim());
            else if (value.isObject() && value.path("name").isTextual()) values.add(value.path("name").asText().trim());
        });
        return values;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}