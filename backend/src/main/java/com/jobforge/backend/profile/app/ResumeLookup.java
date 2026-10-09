package com.jobforge.backend.profile.app;

import java.util.UUID;

public interface ResumeLookup {

    boolean ownsActiveResume(UUID seekerUserId, UUID resumeId);
}
