package com.jobforge.backend.user.app;

import java.util.List;
import java.util.UUID;

public interface UserSearchRepository {

    List<UUID> findSeekerIds(String likePattern, int limit);
}
