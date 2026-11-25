package com.jobforge.aiservice.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class LocalTemplateProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalTemplateProvider provider = new LocalTemplateProvider(mapper);

    @Test
    void ranksPublicJobsBySkillOverlapWithoutInventingJobIds() throws Exception {
        String input = """
                {"context":{
                  "profile":{"skills":["Java","Spring Boot"]},
                  "jobs":[
                    {"id":"job-1","title":"Java Engineer","companyName":"Acme","skills":["Java","Spring Boot","Docker"]},
                    {"id":"job-2","title":"Python Engineer","companyName":"Beta","skills":["Python","Django"]}
                  ]
                }}
                """;
        var result = provider.generate(new AIProvider.ProviderRequest("JOB_RECOMMENDATION", input, "trace"));
        JsonNode output = mapper.readTree(result.output());
        assertEquals(2, output.path("recommendations").size());
        assertEquals("job-1", output.path("recommendations").get(0).path("jobId").asText());
        assertEquals(78, output.path("recommendations").get(0).path("matchScore").asInt());
        assertTrue(output.path("recommendations").get(0).path("matchedSkills").toString().contains("Java"));
    }

    @Test
    void careerInsightsUseProfileCompletenessAndPublicJobSkillDemand() throws Exception {
        String input = """
                {"context":{
                  "profile":{"profileCompleteness":65,"headline":"","summary":"","skills":["Java"],"location":{"city":"Pune"}},
                  "jobs":[
                    {"skills":["Java","Docker","Kubernetes"]},
                    {"skills":["Docker","AWS"]}
                  ]
                }}
                """;
        var result = provider.generate(new AIProvider.ProviderRequest("CAREER_INSIGHT", input, "trace"));
        JsonNode output = mapper.readTree(result.output());
        assertEquals(65, output.path("profileStrength").path("score").asInt());
        assertTrue(output.path("profileStrength").path("suggestions").size() >= 2);
        assertTrue(output.path("skillGaps").toString().contains("Docker"));
        assertEquals(2, output.path("marketSnapshot").path("sampledJobs").asInt());
    }
    @Test
    void ranksRecruiterApplicantsAgainstTheSelectedJobsRequiredSkills() throws Exception {
        String input = """
                {"context":{
                  "job":{"title":"Platform Engineer","skills":["Linux","Docker","Kubernetes"]},
                  "candidates":[
                    {"candidateRef":"candidate-1","headline":"DevOps Engineer","skills":["Linux","Docker","Kubernetes"]},
                    {"profileId":"p2","headline":"Java Developer","skills":["Java","Spring"]}
                  ]
                }}
                """;
        var result = provider.generate(new AIProvider.ProviderRequest("CANDIDATE_RECOMMENDATION", input, "trace"));
        JsonNode output = mapper.readTree(result.output());
        assertEquals(2, output.path("candidates").size());
        assertEquals("candidate-1", output.path("candidates").get(0).path("candidateRef").asText());
        assertEquals(100, output.path("candidates").get(0).path("matchScore").asInt());
    }

}
