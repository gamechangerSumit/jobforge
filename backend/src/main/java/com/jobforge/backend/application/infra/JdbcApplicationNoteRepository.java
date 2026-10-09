package com.jobforge.backend.application.infra;

import com.jobforge.backend.application.app.ApplicationNoteRepository;
import com.jobforge.backend.application.domain.ApplicationNote;
import com.jobforge.backend.shared.persistence.Db;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcApplicationNoteRepository implements ApplicationNoteRepository {

    private static final String COLUMNS = "id, application_id, author_user_id, body, created_at, updated_at";

    private final JdbcClient jdbc;

    public JdbcApplicationNoteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ApplicationNote n, Instant now) {
        jdbc.sql("""
                INSERT INTO core.application_notes (id, application_id, author_user_id, body, created_at, updated_at)
                VALUES (:id, :app, :author, :body, :now, :now)
                """).param("id", n.id()).param("app", n.applicationId()).param("author", n.authorUserId())
                .param("body", n.body()).param("now", Db.ts(now)).update();
    }

    @Override
    public List<ApplicationNote> listByApplication(UUID applicationId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM core.application_notes WHERE application_id = :app AND deleted_at IS NULL ORDER BY created_at, id")
                .param("app", applicationId).query(JdbcApplicationNoteRepository::map).list();
    }

    @Override
    public Optional<ApplicationNote> findById(UUID applicationId, UUID noteId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM core.application_notes WHERE id = :id AND application_id = :app AND deleted_at IS NULL")
                .param("id", noteId).param("app", applicationId).query(JdbcApplicationNoteRepository::map).optional();
    }

    @Override
    public void updateBody(UUID noteId, String body, Instant now) {
        jdbc.sql("UPDATE core.application_notes SET body = :body, updated_at = :now WHERE id = :id")
                .param("body", body).param("now", Db.ts(now)).param("id", noteId).update();
    }

    @Override
    public void softDelete(UUID noteId, Instant now) {
        jdbc.sql("UPDATE core.application_notes SET deleted_at = :now, updated_at = :now WHERE id = :id")
                .param("now", Db.ts(now)).param("id", noteId).update();
    }

    private static ApplicationNote map(ResultSet rs, int row) throws SQLException {
        return new ApplicationNote(Db.uuid(rs, "id"), Db.uuid(rs, "application_id"), Db.uuid(rs, "author_user_id"),
                rs.getString("body"), Db.instant(rs, "created_at"), Db.instant(rs, "updated_at"));
    }
}
