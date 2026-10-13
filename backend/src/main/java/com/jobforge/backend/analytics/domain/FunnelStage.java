package com.jobforge.backend.analytics.domain;

import java.util.List;

/** Ordered hiring funnel stages for the proposed, unmerged Session 5 contract change (API_CONTRACT §12.10.1). Names equal the application_status values. */
public enum FunnelStage {
    SUBMITTED,
    UNDER_REVIEW,
    SHORTLISTED,
    INTERVIEW,
    OFFERED,
    HIRED;

    public static final List<FunnelStage> ORDER = List.of(values());

    /** 1-based rank in the funnel. */
    public int rank() {
        return ordinal() + 1;
    }
}
