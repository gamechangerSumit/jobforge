package com.jobforge.aiservice.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.aiservice.domain.AiOperation;
import com.jobforge.aiservice.domain.CallerIdentity;
import com.jobforge.aiservice.error.AiErrorCode;
import com.jobforge.aiservice.error.AiServiceException;
import com.jobforge.aiservice.persistence.AiQuotaStore;
import com.jobforge.aiservice.persistence.AiRequestStore;
import com.jobforge.aiservice.provider.AIProvider;
import com.jobforge.aiservice.provider.AIProviderSelector;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Orchestrates idempotency, durable quota reservation, provider execution and validated persistence. */
@Service
public class AiExecutionService {
    public record ExecutionResult(UUID requestId, AiOperation operation, String provider, String model,
            String schemaVersion, JsonNode output, JsonNode quality, AiQuotaStore.QuotaSnapshot quota,
            boolean replayed) {}
    private record Started(UUID requestId, boolean replayed, AiRequestStore.RequestRow previous,
            AiQuotaStore.QuotaSnapshot quota) {}

    private final TransactionTemplate transactions;
    private final AiRequestStore requests;
    private final AiQuotaStore quotas;
    private final InputSanitizer sanitizer;
    private final AIProviderSelector providers;
    private final OutputValidators validators;
    private final QualityScorer qualityScorer;
    private final ObjectMapper mapper;

    public AiExecutionService(TransactionTemplate transactions, AiRequestStore requests, AiQuotaStore quotas,
            InputSanitizer sanitizer, AIProviderSelector providers, OutputValidators validators,
            QualityScorer qualityScorer, ObjectMapper mapper) {
        this.transactions=transactions; this.requests=requests; this.quotas=quotas;
        this.sanitizer=sanitizer; this.providers=providers; this.validators=validators;
        this.qualityScorer=qualityScorer; this.mapper=mapper;
    }

    public ExecutionResult execute(CallerIdentity caller, AiOperation operation, JsonNode rawInput,
            UUID idempotencyKey, String traceId) {
        if (!operation.allows(caller.role())) throw new AiServiceException(AiErrorCode.ACCESS_DENIED,"Your role cannot perform this AI operation.");
        if (operation.idempotencyKeyRequired() && idempotencyKey==null)
            throw AiServiceException.validation("Idempotency-Key","NOT_BLANK","A UUID Idempotency-Key is required.");
        JsonNode providerInput=sanitizer.sanitize(rawInput);
        String inputHash=sanitizer.hash(providerInput);
        AIProvider provider=providers.select();
        AIProvider.ProviderInfo info=provider.info();

        Started started=transactions.execute(status -> {
            if (idempotencyKey!=null) {
                var existing=requests.findByIdempotencyKey(caller.userId(),idempotencyKey);
                if(existing.isPresent()) {
                    var row=existing.get();
                    if(!row.inputHash().equals(inputHash))
                        throw new AiServiceException(AiErrorCode.CONFLICT,"Idempotency-Key was already used with a different request body.");
                    if(row.status().name().equals("SUCCEEDED")) {
                        return new Started(row.id(),true,row,quotas.snapshot(caller.userId(),caller.role()));
                    }
                    throw new AiServiceException(AiErrorCode.CONFLICT,"A request with this Idempotency-Key is already in progress or has failed.");
                }
            }
            UUID requestId=UUID.randomUUID();
            quotas.reserve(caller.userId(),caller.role(),requestId,operation.name(),operation.credits());
            requests.insertPending(requestId,caller.userId(),caller.role(),operation,
                    sanitizer.forPersistence(providerInput),inputHash,idempotencyKey,traceId,
                    info.provider(),info.model());
            return new Started(requestId,false,null,null);
        });
        if(started.replayed()) {
            JsonNode validation=started.previous().output()==null?mapper.createObjectNode():mapper.createObjectNode();
            return new ExecutionResult(started.requestId(),operation,started.previous().provider(),
                    started.previous().model(),"job-draft.v1",started.previous().output(),
                    validation,started.quota(),true);
        }

        try {
            AIProvider.ProviderResult generated=provider.generate(new AIProvider.ProviderRequest(
                    operation.name(),providerInput.toString(),traceId));
            JsonNode output=mapper.readTree(generated.output());
            AiOutputValidator.Outcome validation=null;
            JsonNode quality=mapper.createObjectNode();
            String schemaVersion=null;
            if(operation==AiOperation.JOB_GENERATION) {
                AiOutputValidator validator=validators.find(operation)
                        .orElseThrow(AiServiceException::outputInvalid);
                validation=validator.validate(output);
                if(!validation.valid()) throw AiServiceException.outputInvalid();
                schemaVersion=validation.schemaVersion();
                quality=qualityScorer.score(output,validation);
            }
            final JsonNode storedOutput=output;
            final JsonNode storedValidation=validation==null?mapper.createObjectNode():validation.validationResult();
            final String version=schemaVersion;
            final Integer score=validation==null?null:validation.qualityScore();
            transactions.executeWithoutResult(status -> {
                requests.markSucceeded(started.requestId(),storedOutput,version,storedValidation,score,
                        generated.inputTokens(),generated.outputTokens(),operation.credits());
                quotas.commit(started.requestId());
            });
            AiQuotaStore.QuotaSnapshot snapshot=quotas.snapshot(caller.userId(),caller.role());
            return new ExecutionResult(started.requestId(),operation,info.provider(),info.model(),
                    schemaVersion,output,quality,snapshot,false);
        } catch (AiServiceException ex) {
            fail(started.requestId(),ex.code().name());
            throw ex;
        } catch (java.net.SocketTimeoutException ex) {
            fail(started.requestId(),"AI_TIMEOUT");
            throw AiServiceException.timeout();
        } catch (Exception ex) {
            fail(started.requestId(),"AI_PROVIDER_UNAVAILABLE");
            throw AiServiceException.providerUnavailable();
        }
    }

    private void fail(UUID requestId,String code) {
        transactions.executeWithoutResult(status -> {
            requests.markFailed(requestId,code);
            quotas.release(requestId);
        });
    }
}
