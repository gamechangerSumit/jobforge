package com.jobforge.backend.audit.infra;

import com.jobforge.backend.audit.app.AuditLogs;
import com.jobforge.backend.audit.app.AuditLogs.Filter;
import com.jobforge.backend.audit.app.AuditLogs.Row;
import com.jobforge.backend.shared.persistence.Db;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditLogRepository implements AuditLogs.Repository {

    private final JdbcClient jdbc;

    public JdbcAuditLogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static String where(Filter f) {
        StringBuilder sb = new StringBuilder(" WHERE 1=1");
        if (f.actorId() != null) {
            sb.append(" AND actor_user_id = :actor");
        }
        if (f.action() != null) {
            sb.append(" AND action = :action");
        }
        if (f.entityType() != null) {
            sb.append(" AND entity_type = :etype");
        }
        if (f.entityId() != null) {
            sb.append(" AND entity_id = :eid");
        }
        if (f.from() != null) {
            sb.append(" AND occurred_at >= :from");
        }
        if (f.to() != null) {
            sb.append(" AND occurred_at <= :to");
        }
        return sb.toString();
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Filter f) {
        if (f.actorId() != null) {
            spec = spec.param("actor", f.actorId());
        }
        if (f.action() != null) {
            spec = spec.param("action", f.action());
        }
        if (f.entityType() != null) {
            spec = spec.param("etype", f.entityType());
        }
        if (f.entityId() != null) {
            spec = spec.param("eid", f.entityId());
        }
        if (f.from() != null) {
            spec = spec.param("from", Db.ts(f.from()));
        }
        if (f.to() != null) {
            spec = spec.param("to", Db.ts(f.to()));
        }
        return spec;
    }

    private static Row map(ResultSet rs, int n) throws SQLException {
        return new Row(Db.uuid(rs, "id"), Db.instant(rs, "occurred_at"), Db.uuid(rs, "actor_user_id"),
                rs.getString("actor_role"), rs.getString("source"), rs.getString("action"),
                rs.getString("entity_type"), Db.uuid(rs, "entity_id"), rs.getString("outcome"),
                rs.getString("before_state"), rs.getString("after_state"), rs.getString("metadata"),
                rs.getString("ip"), rs.getString("user_agent"), Db.uuid(rs, "request_id"));
    }

    @Override
    public List<Row> search(Filter f, int limit, int offset) {
        String sql = """
                SELECT id, occurred_at, actor_user_id, actor_role, source, action, entity_type, entity_id, outcome,
                       before_state::text AS before_state, after_state::text AS after_state,
                       metadata::text AS metadata, host(ip) AS ip, user_agent, request_id
                  FROM core.audit_logs
                """ + where(f) + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset";
        return bind(jdbc.sql(sql), f).param("limit", limit).param("offset", offset).query(JdbcAuditLogRepository::map).list();
    }

    @Override
    public long count(Filter f) {
        return bind(jdbc.sql("SELECT count(*) FROM core.audit_logs" + where(f)), f).query(Long.class).single();
    }
}
