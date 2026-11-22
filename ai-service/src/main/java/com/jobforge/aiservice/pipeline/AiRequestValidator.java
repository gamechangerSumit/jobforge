package com.jobforge.aiservice.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobforge.aiservice.domain.AiOperation;
import com.jobforge.aiservice.error.AiServiceException;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Enforces request-side constraints documented in API_CONTRACT §14 before quota reservation. */
@Component
public class AiRequestValidator {
    private static final Set<String> EXPERIENCE=Set.of("INTERN","ENTRY","MID","SENIOR","LEAD","EXECUTIVE");
    private static final Set<String> EMPLOYMENT=Set.of("FULL_TIME","PART_TIME","CONTRACT","INTERNSHIP","FREELANCE");
    private static final Set<String> WORK_MODE=Set.of("ONSITE","HYBRID","REMOTE");
    private static final Set<String> TONES=Set.of("PROFESSIONAL","FRIENDLY","CONCISE");

    public void validate(AiOperation operation,JsonNode body) {
        if(body==null||!body.isObject()) fail("body","INVALID_TYPE","Request body must be a JSON object.");
        switch(operation) {
            case JOB_GENERATION -> {
                unknown(body,Set.of("title","companyId","experienceLevel","employmentType","workMode",
                        "location","skills","tone","notes","language","context"));
                jobDraft(body);
            }
            case JOB_VALIDATION -> {
                unknown(body,Set.of("jobId","job","context"));
                jobValidation(body);
            }
            case JOB_RECOMMENDATION, CANDIDATE_RECOMMENDATION, CAREER_INSIGHT -> {
                unknown(body,Set.of("context"));
                if(!body.has("context")||!body.path("context").isObject())
                    fail("context","NOT_NULL","A trusted backend context object is required.");
            }
        }
    }

    private void jobDraft(JsonNode body) {
        text(body,"title",3,120,true);
        uuid(body,"companyId");
        enumValue(body,"experienceLevel",EXPERIENCE);
        enumValue(body,"employmentType",EMPLOYMENT);
        enumValue(body,"workMode",WORK_MODE);
        enumValue(body,"tone",TONES);
        if(!"en".equals(body.path("language").asText()))
            fail("language","INVALID_ENUM","Only en is supported in v1.");
        JsonNode location=body.path("location");
        if(!location.isObject()) fail("location","NOT_NULL","A location object is required.");
        text(location,"city",1,100,true);
        text(location,"state",1,100,true);
        text(location,"country",2,2,true);
        JsonNode skills=body.path("skills");
        if(!skills.isArray()||skills.size()<1||skills.size()>20)
            fail("skills","SIZE","Must contain between 1 and 20 skills.");
        for(int i=0;i<skills.size();i++) {
            JsonNode skill=skills.get(i);
            if(!skill.isTextual()||skill.asText().isBlank()||skill.asText().length()>50)
                fail("skills["+i+"]","SIZE","Each skill must contain 1 to 50 characters.");
        }
        JsonNode notes=body.path("notes");
        if(!notes.isMissingNode()&&!notes.isNull()&&(!notes.isTextual()||notes.asText().length()>1000))
            fail("notes","SIZE","Notes must be at most 1000 characters.");
    }

    private void jobValidation(JsonNode body) {
        boolean hasId=body.hasNonNull("jobId");
        boolean hasJob=body.hasNonNull("job");
        if(hasId==hasJob) fail("job","INVALID_COMBINATION","Provide exactly one of jobId or job.");
        if(hasId) uuid(body,"jobId");
        if(hasJob&&!body.path("job").isObject()) fail("job","INVALID_TYPE","job must be an object.");
    }

    private void unknown(JsonNode body,Set<String> allowed) {
        Iterator<String> fields=body.fieldNames();
        while(fields.hasNext()) {
            String field=fields.next();
            if(!allowed.contains(field)) fail(field,"UNKNOWN_FIELD","Unknown request field.");
        }
    }
    private void text(JsonNode body,String field,int min,int max,boolean required) {
        JsonNode value=body.path(field);
        if(!value.isTextual()||(required&&value.asText().isBlank())||value.asText().length()<min||value.asText().length()>max)
            fail(field,"SIZE","Must be a string between "+min+" and "+max+" characters.");
    }
    private void enumValue(JsonNode body,String field,Set<String> values) {
        String value=body.path(field).asText("");
        if(value.isBlank()||!values.contains(value)) fail(field,"INVALID_ENUM","Value is not supported by the v1 contract.");
    }
    private void uuid(JsonNode body,String field) {
        try { UUID.fromString(body.path(field).asText("")); }
        catch(IllegalArgumentException ex) { fail(field,"PATTERN","Must be a UUID."); }
    }
    private void fail(String field,String code,String message) {
        throw AiServiceException.validation(field,code,message);
    }
}
