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

    public record QuotaPolicyRequest(String role,String period,Integer creditLimit,Integer rateLimitPerMinute,Boolean active) {}
    public record QuotaOverrideRequest(String period,Integer creditLimit,String expiresAt,String reason) {}

    @GetMapping("/admin/quota-policies")
    public Map<String,Object> quotaPolicies(@RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {
        requireAdmin(userId,role);
        return Map.of("data",quotas.listPolicies());
    }

    @PutMapping("/admin/quota-policies")
    public Map<String,Object> updateQuotaPolicy(@RequestBody QuotaPolicyRequest body,
            @RequestHeader("X-User-Id") String userId,@RequestHeader("X-User-Role") String role) {
        CallerIdentity admin=requireAdmin(userId,role);
        if(body.role()==null||!CallerIdentity.ROLES.contains(body.role()))
            throw AiServiceException.validation("role","INVALID_ENUM","Unsupported user role.");
        validatePeriod(body.period());
        if(body.creditLimit()==null||body.creditLimit()<0||body.rateLimitPerMinute()==null||body.rateLimitPerMinute()<1||body.active()==null)
            throw AiServiceException.validation("creditLimit","MIN","Quota limits and active flag are required.");
        quotas.upsertPolicy(body.role(),body.period(),body.creditLimit(),body.rateLimitPerMinute(),body.active(),admin.userId());
        return Map.of("data",quotas.listPolicies());
    }

    @PutMapping("/admin/quota-overrides/{targetUserId}")
    public Map<String,Object> putQuotaOverride(@PathVariable UUID targetUserId,@RequestBody QuotaOverrideRequest body,
            @RequestHeader("X-User-Id") String userId,@RequestHeader("X-User-Role") String role) {
        CallerIdentity admin=requireAdmin(userId,role);
        validatePeriod(body.period());
        if(body.creditLimit()==null||body.creditLimit()<0||body.reason()==null||body.reason().isBlank()||body.reason().length()>500)
            throw AiServiceException.validation("creditLimit","MIN","A non-negative limit and reason (1-500 chars) are required.");
        java.time.OffsetDateTime expires=null;
        if(body.expiresAt()!=null&&!body.expiresAt().isBlank()) {
            try { expires=java.time.OffsetDateTime.parse(body.expiresAt()); }
            catch(java.time.format.DateTimeParseException ex) {
                throw AiServiceException.validation("expiresAt","PATTERN","Must be an ISO-8601 timestamp.");
            }
            if(!expires.isAfter(java.time.OffsetDateTime.now()))
                throw AiServiceException.validation("expiresAt","FUTURE","Must be in the future.");
        }
        quotas.putOverride(targetUserId,body.period(),body.creditLimit(),expires,body.reason(),admin.userId());
        return Map.of("data",Map.of("userId",targetUserId,"period",body.period(),"creditLimit",body.creditLimit(),
                "expiresAt",body.expiresAt()==null?"":body.expiresAt()));
    }

    @DeleteMapping("/admin/quota-overrides/{targetUserId}")
    public ResponseEntity<Void> deleteQuotaOverride(@PathVariable UUID targetUserId,
            @RequestParam(required=false) String period,@RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {
        requireAdmin(userId,role);
        if(period==null||period.isBlank()) quotas.deleteOverrides(targetUserId);
        else {
            validatePeriod(period);
            quotas.deleteOverride(targetUserId,period);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/usage")
    public Map<String,Object> adminUsage(@RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {
        requireAdmin(userId,role);
        return Map.of("data",quotas.adminUsage());
    }

    private CallerIdentity requireAdmin(String userId,String role) {
        CallerIdentity caller=CallerResolver.resolve(userId,role);
        if(!"ADMIN".equals(caller.role()))
            throw new AiServiceException(com.jobforge.aiservice.error.AiErrorCode.ACCESS_DENIED,"Administrator role is required.");
        return caller;
    }

    private void validatePeriod(String period) {
        if(!"DAILY".equals(period)&&!"MONTHLY".equals(period))
            throw AiServiceException.validation("period","INVALID_ENUM","Period must be DAILY or MONTHLY.");
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
