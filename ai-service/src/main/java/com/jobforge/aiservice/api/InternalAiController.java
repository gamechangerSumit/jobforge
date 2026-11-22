package com.jobforge.aiservice.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobforge.aiservice.domain.AiOperation;
import com.jobforge.aiservice.domain.CallerIdentity;
import com.jobforge.aiservice.error.AiServiceException;
import com.jobforge.aiservice.persistence.AiCursorCodec;
import com.jobforge.aiservice.persistence.AiQuotaStore;
import com.jobforge.aiservice.persistence.AiRequestStore;
import com.jobforge.aiservice.pipeline.AiExecutionService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final AiQuotaStore quotas;
    private final AiRequestStore requests;
    private final AiCursorCodec cursors;

    public InternalAiController(AiExecutionService execution,AiQuotaStore quotas,
            AiRequestStore requests,AiCursorCodec cursors) {
        this.execution=execution; this.quotas=quotas; this.requests=requests; this.cursors=cursors;
    }

    @PostMapping("/job-drafts")
    public ResponseEntity<Map<String,Object>> jobDraft(@RequestBody JsonNode body,
            @RequestHeader("X-User-Id") String userId,@RequestHeader("X-User-Role") String role,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value="X-Request-Id",required=false) String traceId) {
        return execute(AiOperation.JOB_GENERATION,body,userId,role,idempotencyKey,traceId);
    }

    @PostMapping("/job-validations")
    public ResponseEntity<Map<String,Object>> jobValidation(@RequestBody JsonNode body,
            @RequestHeader("X-User-Id") String userId,@RequestHeader("X-User-Role") String role,
            @RequestHeader(value="Idempotency-Key",required=false) String idempotencyKey,
            @RequestHeader(value="X-Request-Id",required=false) String traceId) {
        return execute(AiOperation.JOB_VALIDATION,body,userId,role,idempotencyKey,traceId);
    }

    @PostMapping("/recommendations/jobs")
    public ResponseEntity<Map<String,Object>> recommendations(@RequestBody JsonNode body,
            @RequestHeader("X-User-Id") String userId,@RequestHeader("X-User-Role") String role,
            @RequestHeader(value="Idempotency-Key",required=false) String idempotencyKey,
            @RequestHeader(value="X-Request-Id",required=false) String traceId) {
        return execute(AiOperation.JOB_RECOMMENDATION,body,userId,role,idempotencyKey,traceId);
    }

    @PostMapping("/career-insights")
    public ResponseEntity<Map<String,Object>> careerInsights(@RequestBody JsonNode body,
            @RequestHeader("X-User-Id") String userId,@RequestHeader("X-User-Role") String role,
            @RequestHeader(value="Idempotency-Key",required=false) String idempotencyKey,
            @RequestHeader(value="X-Request-Id",required=false) String traceId) {
        return execute(AiOperation.CAREER_INSIGHT,body,userId,role,idempotencyKey,traceId);
    }

    @GetMapping("/usage")
    public Map<String,Object> usage(@RequestParam UUID userId,@RequestHeader("X-User-Id") String headerUserId,
            @RequestHeader("X-User-Role") String role) {
        CallerIdentity caller=CallerResolver.resolve(headerUserId,role);
        if(!caller.userId().equals(userId)) throw new AiServiceException(
                com.jobforge.aiservice.error.AiErrorCode.ACCESS_DENIED,"Requested usage does not belong to caller.");
        AiQuotaStore.QuotaSnapshot snapshot=quotas.snapshot(caller.userId(),caller.role());
        List<Map<String,Object>> operations=new ArrayList<>();
        for(AiOperation operation:AiOperation.values()) {
            if(operation.allows(caller.role())) operations.add(Map.of("operation",operation.name(),"credits",operation.credits()));
        }
        return Map.of("data",Map.of("period",snapshot.period(),"limit",snapshot.limit(),"used",snapshot.used(),
                "remaining",snapshot.remaining(),"resetsAt",snapshot.resetsAt().toString(),
                "rateLimitPerMinute",snapshot.rateLimitPerMinute(),"operations",operations));
    }

    @GetMapping("/requests")
    public Map<String,Object> history(@RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String cursor) {
        CallerIdentity caller=CallerResolver.resolve(userId,role);
        if(limit<1||limit>50) throw AiServiceException.validation("limit","MAX","Must be between 1 and 50.");
        AiCursorCodec.Cursor decoded=cursor==null||cursor.isBlank()?null:cursors.decode(cursor);
        AiRequestStore.RequestPage page=requests.pageOwned(caller.userId(),decoded,limit);
        List<Map<String,Object>> data=page.items().stream().map(this::summary).toList();
        String next=null;
        if(page.hasNext()&&!page.items().isEmpty()) {
            var last=page.items().get(page.items().size()-1);
            next=cursors.encode(last.createdAt(),last.id());
        }
        Map<String,Object> cursorMeta=new LinkedHashMap<>();
        cursorMeta.put("next",next);
        cursorMeta.put("hasNext",page.hasNext());
        cursorMeta.put("limit",limit);
        return Map.of("data",data,"meta",Map.of("cursor",cursorMeta));
    }

    @GetMapping("/requests/{id}")
    public Map<String,Object> detail(@PathVariable UUID id,@RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {
        CallerIdentity caller=CallerResolver.resolve(userId,role);
        var row=requests.findOwned(caller.userId(),id).orElseThrow(AiServiceException::notFound);
        Map<String,Object> data=summary(row);
        data.put("output",row.output());
        data.put("schemaVersion",row.schemaVersion());
        data.put("validationResult",row.validationResult());
        return Map.of("data",data);
    }

    private Map<String,Object> summary(AiRequestStore.RequestRow row) {
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("id",row.id()); data.put("operation",row.operation().name());
        data.put("status",row.status().name()); data.put("provider",row.provider());
        data.put("model",row.model()); data.put("qualityScore",row.qualityScore());
        data.put("creditsCharged",row.creditsCharged()); data.put("createdAt",row.createdAt().toString());
        return data;
    }

    private ResponseEntity<Map<String,Object>> execute(AiOperation operation,JsonNode body,String userId,
            String role,String key,String traceId) {
        CallerIdentity caller=CallerResolver.resolve(userId,role);
        UUID idempotency=CallerResolver.optionalUuid("Idempotency-Key",key);
        String requestId;
        try { requestId=UUID.fromString(traceId==null?"":traceId).toString(); }
        catch(IllegalArgumentException ex) { requestId=UUID.randomUUID().toString(); }
        var result=execution.execute(caller,operation,body,idempotency,requestId);
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
                    "limit",quota.limit(),"period",quota.period(),"resetsAt",quota.resetsAt().toString()));
        } else {
            data.put("result",result.output());
        }
        return ResponseEntity.status(HttpStatus.OK)
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
                .header("X-Request-Id",requestId)
                .body(Map.of("data",data));
    }
}
