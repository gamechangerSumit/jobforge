package com.jobforge.backend.job.domain;

public record JobSalary(Long min, Long max, String currency, SalaryPeriod period) {

    public boolean allNull() {
        return min == null && max == null && currency == null && period == null;
    }
}
