package com.jobforge.backend.user.app;

import com.jobforge.backend.user.facade.UserSummaryFacade;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSummaryService implements UserSummaryFacade {

    private final UserSummaryRepository repository;

    public UserSummaryService(UserSummaryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserSummaryView> summaries(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return repository.summaries(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(UserSummaryView::id, v -> v, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummaryView> summary(UUID id) {
        return Optional.ofNullable(summaries(java.util.List.of(id)).get(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, UUID> activeUserIdsByHandle(Collection<String> handles) {
        if (handles == null || handles.isEmpty()) {
            return Map.of();
        }
        return repository.activeIdsByHandle(handles.stream().map(h -> h.toLowerCase(Locale.ROOT)).distinct().toList());
    }
}
