package com.jobforge.aiservice.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobforge.aiservice.domain.AiOperation;
import com.jobforge.aiservice.domain.CallerIdentity;
import com.jobforge.aiservice.pipeline.AiExecutionService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Private backend-to-ai-service endpoints; never expose this controller to browsers. */
@RestController
@RequestMapping("/internal/v1/ai")
public class InternalAiController {
    private final AiExecutionService execution;
    public InternalAiController(AiExecutionService execution) { this.execution=execution; }

    @PostMapping("/job-drafts")
    public ResponseEntity<Map<String,Object>> jobDraft(@RequestBody JsonNode body,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value="X-Request-Id",required=false) String traceId) {
        return execute(AiOperation.JOB_GENERATION,body,userId,role,idempotencyKey,traceId);
    }

    @PostMapping("/job-validations")
    public ResponseEntity<Map<String,Object>> jobValidation(@RequestBody JsonNode body,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value="Idempotency-Key",required=false) String idempotencyKey,
            @RequestHeader(value="X-Request-Id",required=false) String traceId) {
        return execute(AiOperation.JOB_VALIDATION,body,userId,role,idempotencyKey,traceId);
    }

    @PostMapping("/recommendations/jobs")
    public ResponseEntity<Map<String,Object>> recommendations(@RequestBody JsonNode body,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value="Idempotency-Key",required=false) String idempotencyKey,
            @RequestHeader(value="X-Request-Id",required=false) String traceId) {
        return execute(AiOperation.JOB_RECOMMENDATION,body,userId,role,idempotencyKey,traceId);
    }

    @PostMapping("/career-insights")
    public ResponseEntity<Map<String,Object>> careerInsights(@RequestBody JsonNode body,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value="Idempotency-Key",required=false) String idempotencyKey,
            @RequestHeader(value="X-Request-Id",required=false) String traceId) {
        return execute(AiOperation.CAREER_INSIGHT,body,userId,role,idempotencyKey,traceId);
    }

    private ResponseEntity<Map<String,Object>> execute(AiOperation operation,JsonNode body,String userId,
            String role,String key,String traceId) {
        CallerIdentity caller=CallerResolver.resolve(userId,role);
        UUID idempotency=CallerResolver.optionalUuid("Idempotency-Key",key);
        var result=execution.execute(caller,operation,body,idempotency,traceId);
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("requestId",result.requestId());
        data.put("operation",operation.name());
        data.put("provider",result.provider());
        data.put("model",result.model());
        if(operation==AiOperation.JOB_GENERATION) {
            data.put("schemaVersion",result.schemaVersion());
            data.put("draft",result.output());
            data.put("quality",result.quality());
            var quota=result.quota();
            data.put("quota",Map.of("creditsCharged",operation.credits(),"remaining",quota.remaining(),
                    "limit",quota.limit(),"period","DAILY","resetsAt",quota.resetsAt().toString()));
        } else {
            data.put("result",result.output());
        }
        return ResponseEntity.status(HttpStatus.OK)
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .body(Map.of("data",data));
    }
}
