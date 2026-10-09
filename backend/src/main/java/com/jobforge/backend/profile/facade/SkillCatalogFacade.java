package com.jobforge.backend.profile.facade;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Skill catalog access for other modules (jobs). The catalog table is owned by the profile module. */
public interface SkillCatalogFacade {

    record SkillRef(UUID id, String name, String slug) {}

    /** Resolves names case-insensitively, creating unknown skills as {@code is_verified=false}. Key = lower-cased name. */
    Map<String, SkillRef> resolveOrCreate(Collection<String> names);

    Map<UUID, SkillRef> byIds(Collection<UUID> ids);
}
