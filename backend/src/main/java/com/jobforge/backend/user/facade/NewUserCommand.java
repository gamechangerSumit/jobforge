package com.jobforge.backend.user.facade;

import com.jobforge.backend.shared.domain.UserRole;

public record NewUserCommand(
        String email, String passwordHash, UserRole role, String firstName, String lastName, String handle) {

    @Override
    public String toString() {
        return "NewUserCommand[role=" + role + "]";
    }
}
