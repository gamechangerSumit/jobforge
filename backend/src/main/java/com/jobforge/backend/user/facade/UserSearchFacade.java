package com.jobforge.backend.user.facade;

import java.util.List;
import java.util.UUID;

public interface UserSearchFacade {

    /** Active job seekers whose name or handle contains {@code q} (case-insensitive, literal match). */
    List<UUID> findSeekerIdsByNameOrHandle(String q, int limit);
}
