package com.jobforge.aiservice.pipeline;

import com.jobforge.aiservice.config.AiRuntimeProperties;
import com.jobforge.aiservice.persistence.AiQuotaStore;
import com.jobforge.aiservice.persistence.AiRequestStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Recovers quota reservations if the service process dies while a provider call is running. */
@Component
public class AiPendingRequestReaper {
    private final TransactionTemplate transactions;
    private final AiRequestStore requests;
    private final AiQuotaStore quotas;
    private final AiRuntimeProperties properties;

    public AiPendingRequestReaper(TransactionTemplate transactions,AiRequestStore requests,
            AiQuotaStore quotas,AiRuntimeProperties properties) {
        this.transactions=transactions; this.requests=requests; this.quotas=quotas; this.properties=properties;
    }

    @Scheduled(fixedDelayString="${jobforge.ai.quota.sweep-interval-ms:60000}")
    public void reap() {
        transactions.executeWithoutResult(status -> {
            requests.expirePending(properties.quota().reservationTtlSeconds());
            quotas.releaseTimedOutReservations();
        });
    }
}
