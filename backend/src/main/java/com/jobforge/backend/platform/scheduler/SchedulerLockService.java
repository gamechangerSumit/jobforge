package com.jobforge.backend.platform.scheduler;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class SchedulerLockService {

    private final JdbcTemplate jdbc;

    private final String owner =
            "jobforge-backend-" + UUID.randomUUID();

    public SchedulerLockService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean tryLock(
            String name,
            Duration lockFor
    ) {
        int updated = jdbc.update(
                """
                INSERT INTO platform.shedlock
                    (name, lock_until, locked_at, locked_by)
                VALUES
                    (?, now() + (? * interval '1 millisecond'), now(), ?)
                ON CONFLICT (name)
                DO UPDATE SET
                    lock_until = now() + (? * interval '1 millisecond'),
                    locked_at = now(),
                    locked_by = EXCLUDED.locked_by
                WHERE platform.shedlock.lock_until <= now()
                """,
                name,
                lockFor.toMillis(),
                owner,
                lockFor.toMillis()
        );

        return updated == 1;
    }

    public void unlock(String name) {
        jdbc.update(
                """
                UPDATE platform.shedlock
                SET lock_until = now()
                WHERE name = ?
                  AND locked_by = ?
                """,
                name,
                owner
        );
    }
}