package com.jobforge.backend.profile.infra;

import com.jobforge.backend.profile.app.SkillRepository;
import com.jobforge.backend.profile.facade.SkillCatalogFacade.SkillRef;
import com.jobforge.backend.shared.persistence.Db;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSkillRepository implements SkillRepository {

    private final JdbcClient jdbc;

    public JdbcSkillRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SkillRef> findByName(String name) {
        return jdbc.sql("SELECT id, name, slug FROM core.skills WHERE name = :name")
                .param("name", name).query(JdbcSkillRepository::map).optional();
    }

    @Override
    public Optional<SkillRef> insertUnverified(UUID id, String name, String slug) {
        return jdbc.sql("""
                INSERT INTO core.skills (id, name, slug, is_verified) VALUES (:id, :name, :slug, false)
                ON CONFLICT DO NOTHING RETURNING id, name, slug
                """)
                .param("id", id).param("name", name).param("slug", slug)
                .query(JdbcSkillRepository::map).optional();
    }

    @Override
    public Map<UUID, SkillRef> findByIds(Collection<UUID> ids) {
        Map<UUID, SkillRef> result = new HashMap<>();
        jdbc.sql("SELECT id, name, slug FROM core.skills WHERE id IN (:ids)").param("ids", ids)
                .query(JdbcSkillRepository::map).list().forEach(s -> result.put(s.id(), s));
        return result;
    }

    @Override
    public java.util.List<SkillRef> search(String q, int limit) {
        String escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return jdbc.sql("""
                SELECT id, name::text AS name, slug FROM core.skills
                 WHERE name::text ILIKE :contains ESCAPE '\\'
                 ORDER BY (name::text ILIKE :prefix ESCAPE '\\') DESC, is_verified DESC, name::text
                 LIMIT :limit
                """)
                .param("contains", "%" + escaped + "%").param("prefix", escaped + "%").param("limit", limit)
                .query(JdbcSkillRepository::map).list();
    }

    private static SkillRef map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new SkillRef(Db.uuid(rs, "id"), rs.getString("name"), rs.getString("slug"));
    }
}
