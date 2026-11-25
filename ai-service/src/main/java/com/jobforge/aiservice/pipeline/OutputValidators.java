package com.jobforge.aiservice.pipeline;

import com.jobforge.aiservice.domain.AiOperation;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Registry rejects duplicate validators and keeps unsupported operations disabled. */
@Component
public class OutputValidators {
    private final Map<AiOperation, AiOutputValidator> byOperation = new EnumMap<>(AiOperation.class);
    public OutputValidators(List<AiOutputValidator> validators) {
        for (AiOutputValidator validator : validators) {
            if (byOperation.put(validator.operation(), validator) != null) {
                throw new IllegalStateException("Two output validators registered for " + validator.operation());
            }
        }
    }
    public Optional<AiOutputValidator> find(AiOperation operation) {
        return Optional.ofNullable(byOperation.get(operation));
    }
}
