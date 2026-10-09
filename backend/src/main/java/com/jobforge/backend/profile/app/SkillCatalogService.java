package com.jobforge.backend.profile.app;

import com.jobforge.backend.profile.facade.SkillCatalogFacade;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillCatalogService implements SkillCatalogFacade {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final java.time.Duration SEARCH_TTL = java.time.Duration.ofSeconds(120);

    private final SkillRepository skills;
    private final com.jobforge.backend.shared.cache.CacheService cache;

    public SkillCatalogService(SkillRepository skills, com.jobforge.backend.shared.cache.CacheService cache) {
        this.skills = skills;
        this.cache = cache;
    }

    @Override
    @Transactional
    public Map<String, SkillRef> resolveOrCreate(Collection<String> names) {
        Map<String, SkillRef> result = new HashMap<>();
        for (String raw : names) {
            String name = raw.trim().replaceAll("\\s+", " ");
            String key = name.toLowerCase(Locale.ROOT);
            if (name.isEmpty() || result.containsKey(key)) {
                continue;
            }
            SkillRef ref = skills.findByName(name).orElseGet(() -> create(name));
            result.put(key, ref);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SkillRef> byIds(Collection<UUID> ids) {
        return ids.isEmpty() ? Map.of() : skills.findByIds(ids);
    }

    @Transactional(readOnly = true)
    public java.util.List<SkillRef> search(String q, int limit) {
        if (q == null || q.isBlank()) {
            return java.util.List.of();
        }
        String term = q.trim();
        int capped = Math.max(1, Math.min(limit, 20));
        // Public, caller-independent data: cached briefly (new skills appear within the TTL).
        String key = "skills:q:" + term.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#.\\- ]", "_") + ":" + capped;
        return cache.getOrLoad(key, SEARCH_TTL, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<SkillRef>>() { },
                () -> skills.search(term, capped));
    }

    private SkillRef create(String name) {
        String base = slugify(name);
        String slug = base;
        for (int attempt = 0; attempt < 4; attempt++) {
            var inserted = skills.insertUnverified(UUID.randomUUID(), name, slug);
            if (inserted.isPresent()) {
                return inserted.get();
            }
            var existing = skills.findByName(name); // concurrent creation of the same name
            if (existing.isPresent()) {
                return existing.get();
            }
            slug = trimTo(base, 55) + "-" + String.format("%04x", RANDOM.nextInt(0x10000)); // slug collision, other name
        }
        throw new IllegalStateException("Could not allocate a skill slug");
    }

    static String slugify(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return trimTo(slug.isEmpty() ? "skill" : slug, 60);
    }

    private static String trimTo(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
