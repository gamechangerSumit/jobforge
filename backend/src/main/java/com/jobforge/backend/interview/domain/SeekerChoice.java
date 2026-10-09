package com.jobforge.backend.interview.domain;

/** Request value of {@code POST /interviews/{id}/respond} ({@code CONFIRM|DECLINE}, API_CONTRACT §12.7). */
public enum SeekerChoice {
    CONFIRM, DECLINE
}
