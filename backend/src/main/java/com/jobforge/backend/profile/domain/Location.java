package com.jobforge.backend.profile.domain;

/** API_CONTRACT §1 Location. */
public record Location(String city, String state, String country) {

    public boolean allNull() {
        return city == null && state == null && country == null;
    }
}
