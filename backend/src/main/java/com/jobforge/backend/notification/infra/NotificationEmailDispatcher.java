package com.jobforge.backend.notification.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Small durable email-outbox worker. Failed deliveries retry with bounded backoff and then become FAILED. */
@Component
public class NotificationEmailDispatcher {
    private final JdbcTemplate jdbc;
    private final JavaMailSender mail;
    private final ObjectMapper mapper;
    private final String from;

    public NotificationEmailDispatcher(JdbcTemplate jdbc, JavaMailSender mail, ObjectMapper mapper,
            @Value("${jobforge.auth.mail-from:no-reply@jobforge.local}") String from) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.mapper = mapper;
        this.from = from;
    }

    @Scheduled(fixedDelay = 15_000)
    @Transactional
    public void dispatch() {
        List<EmailRow> rows = jdbc.query("""
            SELECT id,to_email,template,payload::text,attempts
            FROM platform.email_outbox
            WHERE status='PENDING' AND next_attempt_at <= now() AND attempts < 5
            ORDER BY next_attempt_at,created_at
            LIMIT 10
            FOR UPDATE SKIP LOCKED
            """, (rs, row) -> new EmailRow(rs.getObject("id", java.util.UUID.class), rs.getString("to_email"),
                rs.getString("template"), rs.getString("payload"), rs.getInt("attempts")));
        for (EmailRow row : rows) {
            try {
                JsonNode payload = mapper.readTree(row.payload());
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(row.to());
                message.setSubject(payload.path("title").asText("JobForge notification"));
                message.setText(payload.path("body").asText("You have a new notification in JobForge."));
                mail.send(message);
                jdbc.update("UPDATE platform.email_outbox SET status='SENT',sent_at=?,attempts=attempts+1,last_error=NULL WHERE id=?",
                    Instant.now(), row.id());
            } catch (Exception ex) {
                int attempts = row.attempts() + 1;
                String safeError = ex.getClass().getSimpleName();
                if (safeError.length() > 500) safeError = safeError.substring(0, 500);
                if (attempts >= 5) {
                    jdbc.update("UPDATE platform.email_outbox SET status='FAILED',attempts=?,last_error=? WHERE id=?",
                        attempts, safeError, row.id());
                } else {
                    long delaySeconds = Math.min(3600L, 30L * (1L << Math.min(attempts, 6)));
                    jdbc.update("""
                        UPDATE platform.email_outbox
                        SET attempts=?,last_error=?,next_attempt_at=now() + (? * interval '1 second')
                        WHERE id=?
                        """, attempts, safeError, delaySeconds, row.id());
                }
            }
        }
    }

    private record EmailRow(java.util.UUID id, String to, String template, String payload, int attempts) {}
}
