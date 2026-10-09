package com.jobforge.backend.profile.domain;

/** API_CONTRACT §1 Money (integers, ISO-4217 currency). */
public record ExpectedSalary(Long min, Long max, String currency, SalaryPeriod period) {

    public boolean allNull() {
        return min == null && max == null && currency == null && period == null;
    }
}
