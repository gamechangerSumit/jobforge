package com.jobforge.backend.user.infra;

import com.jobforge.backend.shared.config.ApiPaths;
import com.jobforge.backend.shared.persistence.Db;
import com.jobforge.backend.user.app.UserSummaryRepository;
import com.jobforge.backend.user.facade.UserSummaryFacade.UserSummaryView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Read model over core.users for the community module. Selects only public columns. */
@Repository
public class JdbcUserSummaryRepository implements UserSummaryRepository {

    private final JdbcClient jdbc;

    public JdbcUserSummaryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UserSummaryView> summaries(List<UUID> ids) {
        return jdbc.sql("SELECT id, handle::text AS handle, first_name, last_name, avatar_key, role, status "
                        + "FROM core.users WHERE id IN (:ids)")
                .param("ids", ids)
                .query((rs, n) -> new UserSummaryView(Db.uuid(rs, "id"), rs.getString("handle"),
                        rs.getString("first_name"), rs.getString("last_name"),
                        rs.getString("avatar_key") == null ? null
                                : ApiPaths.BASE + "/users/" + Db.uuid(rs, "id") + "/avatar",
                        rs.getString("role"), "ACTIVE".equals(rs.getString("status"))))
                .list();
    }

    @Override
    public Map<String, UUID> activeIdsByHandle(List<String> lowerCaseHandles) {
        Map<String, UUID> out = new LinkedHashMap<>();
        jdbc.sql("SELECT id, lower(handle::text) AS handle FROM core.users WHERE status = 'ACTIVE' "
                        + "AND lower(handle::text) IN (:handles)")
                .param("handles", lowerCaseHandles)
                .query((rs, n) -> {
                    out.put(rs.getString("handle"), Db.uuid(rs, "id"));
                    return null;
                }).list();
        return out;
    }
}
