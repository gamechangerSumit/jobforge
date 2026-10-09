package com.jobforge.backend.profile.app;

import com.jobforge.backend.profile.facade.SkillCatalogFacade.SkillRef;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SkillRepository {

    Optional<SkillRef> findByName(String name);

    /** Inserts an unverified skill; @return empty when the name or slug already exists. */
    Optional<SkillRef> insertUnverified(UUID id, String name, String slug);

    Map<UUID, SkillRef> findByIds(Collection<UUID> ids);

    /** Autocomplete: names containing {@code q} (case-insensitive), verified skills and prefix matches first. */
    java.util.List<SkillRef> search(String q, int limit);
}
