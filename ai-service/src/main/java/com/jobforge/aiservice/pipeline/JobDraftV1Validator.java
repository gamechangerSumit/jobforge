package com.jobforge.aiservice.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobforge.aiservice.domain.AiOperation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Runtime guard for the job-draft.v1 response contract; never persists unvalidated provider output. */
@Component
public class JobDraftV1Validator implements AiOutputValidator {
    private static final Set<String> FIELDS = Set.of("title","summary","responsibilities","requirements",
            "niceToHave","benefits","skills","employmentType","workMode","experienceLevel","descriptionMarkdown");
    private static final Pattern HTML = Pattern.compile("<\\s*/?\\s*[a-zA-Z][^>]*>");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private final ObjectMapper mapper;

    public JobDraftV1Validator(ObjectMapper mapper) { this.mapper = mapper; }
    @Override public AiOperation operation() { return AiOperation.JOB_GENERATION; }

    @Override
    public Outcome validate(JsonNode output) {
        List<Object> issues = new ArrayList<>();
        if (output == null || !output.isObject()) return invalid("OUTPUT_NOT_OBJECT", "output", "Output must be a JSON object.");
        Iterator<String> names = output.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!FIELDS.contains(name)) issues.add(issue("UNKNOWN_PROPERTY", "ERROR", name, "Unknown output property."));
        }
        text(output, "title", 3, 120, true, issues);
        text(output, "summary", 1, 500, true, issues);
        text(output, "descriptionMarkdown", 1, 10000, true, issues);
        list(output, "responsibilities", 3, 12, true, issues);
        list(output, "requirements", 3, 12, true, issues);
        list(output, "niceToHave", 0, 8, false, issues);
        list(output, "benefits", 0, 10, false, issues);
        JsonNode skills = output.path("skills");
        if (!skills.isArray() || skills.size() < 3 || skills.size() > 20) {
            issues.add(issue("SIZE", "ERROR", "skills", "Must contain between 3 and 20 skills."));
        } else {
            for (int i=0;i<skills.size();i++) {
                JsonNode skill=skills.get(i);
                if (!skill.isObject() || !skill.path("name").isTextual()
                        || skill.path("name").asText().isBlank() || skill.path("name").asText().length()>50
                        || !skill.path("required").isBoolean()) {
                    issues.add(issue("INVALID_SKILL", "ERROR", "skills["+i+"]", "Each skill needs a name (1-50) and required boolean."));
                }
            }
        }
        enumField(output,"employmentType",Set.of("FULL_TIME","PART_TIME","CONTRACT","INTERNSHIP","TEMPORARY"),issues);
        enumField(output,"workMode",Set.of("ONSITE","HYBRID","REMOTE"),issues);
        enumField(output,"experienceLevel",Set.of("ENTRY","JUNIOR","MID","SENIOR","LEAD","EXECUTIVE"),issues);
        scan(output, issues);
        boolean valid = issues.stream().noneMatch(x -> "ERROR".equals(((java.util.Map<?,?>)x).get("severity")));
        int score = valid ? (issues.isEmpty() ? 90 : 85) : 0;
        return new Outcome(valid, "job-draft.v1", score, mapper.valueToTree(java.util.Map.of("issues", issues)));
    }

    private void text(JsonNode root,String field,int min,int max,boolean required,List<Object> issues) {
        JsonNode n=root.path(field);
        if (!n.isTextual() || (required && n.asText().isBlank()) || n.asText().length()<min || n.asText().length()>max)
            issues.add(issue("SIZE","ERROR",field,"Must be a non-empty string within the documented length limit."));
    }
    private void list(JsonNode root,String field,int min,int max,boolean required,List<Object> issues) {
        JsonNode n=root.path(field);
        if (!n.isArray() || n.size()<min || n.size()>max) {
            if (required || (n.isArray() && n.size()>max) || (n.isArray() && n.size()>0))
                issues.add(issue("SIZE","ERROR",field,"List size is outside the documented limit."));
            return;
        }
        for(int i=0;i<n.size();i++) {
            JsonNode item=n.get(i);
            if(!item.isTextual() || item.asText().isBlank() || item.asText().length()>300)
                issues.add(issue("INVALID_ITEM","ERROR",field+"["+i+"]","Items must be non-empty strings up to 300 characters."));
        }
    }
    private void enumField(JsonNode root,String field,Set<String> allowed,List<Object> issues) {
        if(!root.path(field).isTextual() || !allowed.contains(root.path(field).asText()))
            issues.add(issue("INVALID_ENUM","ERROR",field,"Value is not supported by the v1 contract."));
    }
    private void scan(JsonNode node,List<Object> issues) {
        if(node.isTextual()) {
            String value=node.asText();
            if(HTML.matcher(value).find()) issues.add(issue("HTML_NOT_ALLOWED","ERROR","output","HTML markup is not allowed."));
            if(URL.matcher(value).find()) issues.add(issue("URL_NOT_ALLOWED","ERROR","output","URLs are not allowed."));
            if(EMAIL.matcher(value).find()) issues.add(issue("PII_NOT_ALLOWED","ERROR","output","Email addresses are not allowed."));
        } else if(node.isContainerNode()) node.elements().forEachRemaining(child -> scan(child,issues));
    }
    private Outcome invalid(String code,String field,String message) {
        return new Outcome(false,"job-draft.v1",0,mapper.valueToTree(java.util.Map.of("issues",List.of(issue(code,"ERROR",field,message)))));
    }
    private static java.util.Map<String,String> issue(String code,String severity,String field,String message) {
        return java.util.Map.of("code",code,"severity",severity,"field",field,"message",message);
    }
}
