package com.jobforge.backend.job.domain;

public record JobLocation(String city, String state, String country) {

    public boolean allNull() {
        return city == null && state == null && country == null;
    }
}
