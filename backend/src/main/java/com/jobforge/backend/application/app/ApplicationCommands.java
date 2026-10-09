package com.jobforge.backend.application.app;

import java.util.UUID;

public final class ApplicationCommands {

    private ApplicationCommands() {}

    public record Apply(UUID resumeId, String coverLetter) {}
}
