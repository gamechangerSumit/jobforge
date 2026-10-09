package com.jobforge.backend.user.app;

import com.jobforge.backend.user.facade.UserSearchFacade;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSearchService implements UserSearchFacade {

    private final UserSearchRepository repository;

    public UserSearchService(UserSearchRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findSeekerIdsByNameOrHandle(String q, int limit) {
        String escaped = q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return escaped.isEmpty() ? List.of() : repository.findSeekerIds("%" + escaped + "%", Math.min(limit, 200));
    }
}
