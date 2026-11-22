package com.jobforge.aiservice.pipeline;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.aiservice.domain.AiOperation;
import com.jobforge.aiservice.error.AiServiceException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiRequestValidatorTest {
    private final AiRequestValidator validator=new AiRequestValidator();
    private final ObjectMapper mapper=new ObjectMapper();

    @Test void acceptsContractJobDraftRequest() throws Exception {
        var body=mapper.readTree("""
          {"title":"Backend Engineer","companyId":"%s","experienceLevel":"MID",
           "employmentType":"FULL_TIME","workMode":"HYBRID",
           "location":{"city":"Nagpur","state":"Maharashtra","country":"IN"},
           "skills":["java","spring-boot"],"tone":"PROFESSIONAL","notes":"Payments domain","language":"en"}
          """.formatted(UUID.randomUUID()));
        assertThatCode(()->validator.validate(AiOperation.JOB_GENERATION,body)).doesNotThrowAnyException();
    }

    @Test void rejectsUnknownFieldsBeforeQuotaIsReserved() throws Exception {
        var body=mapper.readTree("""
          {"title":"Backend Engineer","companyId":"%s","experienceLevel":"MID",
           "employmentType":"FULL_TIME","workMode":"HYBRID",
           "location":{"city":"Nagpur","state":"Maharashtra","country":"IN"},
           "skills":["java"],"tone":"PROFESSIONAL","language":"en","surprise":true}
          """.formatted(UUID.randomUUID()));
        assertThatThrownBy(()->validator.validate(AiOperation.JOB_GENERATION,body))
                .isInstanceOf(AiServiceException.class).hasMessageContaining("One or more fields");
    }

    @Test void requiresExactlyOneJobValidationTarget() throws Exception {
        var body=mapper.readTree("{\"jobId\":\""+UUID.randomUUID()+"\",\"job\":{}}");
        assertThatThrownBy(()->validator.validate(AiOperation.JOB_VALIDATION,body))
                .isInstanceOf(AiServiceException.class);
    }
}
