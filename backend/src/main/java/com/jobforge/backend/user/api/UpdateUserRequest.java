package com.jobforge.backend.user.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH body: absent or null = unchanged. Avatar settings are delivered with the storage module. */
public record UpdateUserRequest(
        @Size(min = 1, max = 60) String firstName,
        @Size(min = 1, max = 60) String lastName,
        @Size(min = 3, max = 30) @Pattern(regexp = "^[a-z0-9_]+$") String handle) {}
