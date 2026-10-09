package com.jobforge.backend.devseed;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Local-development seed (MASTER_SPEC db/seed/dev). Disabled unless {@code DEV_SEED_ENABLED=true}; the shared password
 * comes from {@code DEV_SEED_PASSWORD} (no default, never committed). Idempotent: does nothing once the admin exists.
 * Creates an admin, an APPROVED recruiter owning a VERIFIED company, a seeker, and 60 published jobs so that search,
 * facets, applications and the recruiter pipeline can be exercised immediately on a fresh database.
 */
@Component
@ConditionalOnProperty(name = "jobforge.dev-seed.enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    static final String ADMIN_EMAIL = "admin@jobforge.local";
    static final String RECRUITER_EMAIL = "recruiter@jobforge.local";
    static final String SEEKER_EMAIL = "seeker@jobforge.local";

    private static final List<String> SKILLS = List.of("Java", "Spring Boot", "PostgreSQL", "React", "TypeScript",
            "Kafka", "Docker", "Kubernetes", "AWS", "Python", "Go", "SQL", "Node.js", "Redis", "Terraform");
    private static final List<String> TITLES = List.of("Backend Engineer", "Frontend Engineer", "Full Stack Developer",
            "DevOps Engineer", "Data Engineer", "Platform Engineer");
    private static final List<String[]> PLACES = List.of(new String[] {"Berlin", "BE", "DE"},
            new String[] {"Austin", "TX", "US"}, new String[] {"Pune", "MH", "IN"}, new String[] {"London", "ENG", "GB"},
            new String[] {"Toronto", "ON", "CA"});
    private static final List<String> MODES = List.of("REMOTE", "HYBRID", "ONSITE");
    private static final List<String> LEVELS = List.of("ENTRY", "MID", "SENIOR", "LEAD");
    private static final List<String> TYPES = List.of("FULL_TIME", "CONTRACT", "PART_TIME", "FULL_TIME");

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String password;

    public DevDataSeeder(JdbcClient jdbc, PasswordEncoder encoder, TransactionTemplate tx, Clock clock,
            @Value("${jobforge.dev-seed.password:}") String password) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.tx = tx;
        this.clock = clock;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.length() < 12) {
            throw new IllegalStateException("DEV_SEED_ENABLED=true requires DEV_SEED_PASSWORD with at least 12 characters");
        }
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM core.users WHERE email = :e)")
                .param("e", ADMIN_EMAIL).query(Boolean.class).single();
        if (exists) {
            log.info("Dev seed skipped: data already present");
            return;
        }
        tx.executeWithoutResult(status -> seed());
        log.info("Dev seed created: {}, {}, {} (password from DEV_SEED_PASSWORD)", ADMIN_EMAIL, RECRUITER_EMAIL, SEEKER_EMAIL);
    }

    private void seed() {
        Instant now = clock.instant();
        OffsetDateTime ts = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        String hash = encoder.encode(password);
        UUID adminId = user(hash, ADMIN_EMAIL, "ADMIN", "Ada", "Admin", "dev_admin", ts);
        UUID recruiterId = user(hash, RECRUITER_EMAIL, "RECRUITER", "Rita", "Recruiter", "dev_recruiter", ts);
        UUID seekerId = user(hash, SEEKER_EMAIL, "JOB_SEEKER", "Sam", "Seeker", "dev_seeker", ts);

        jdbc.sql("INSERT INTO core.seeker_profiles (id, user_id, version, created_at, updated_at) VALUES (:id, :u, 0, :t, :t)")
                .param("id", UUID.randomUUID()).param("u", seekerId).param("t", ts).update();
        jdbc.sql("""
                INSERT INTO core.recruiter_profiles (id, user_id, job_title, approval_status, approved_by, approved_at,
                                                     created_at, updated_at)
                VALUES (:id, :u, 'Head of Talent', 'APPROVED', :admin, :t, :t, :t)
                """).param("id", UUID.randomUUID()).param("u", recruiterId).param("admin", adminId).param("t", ts).update();

        UUID companyId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO core.companies (id, name, slug, description, industry, size_band, website_url, hq_city,
                                            hq_country, verification_status, verified_by, verified_at, created_by)
                VALUES (:id, 'Acme Labs', 'acme-labs', 'Acme Labs builds developer tooling.', 'Software', '51_200',
                        'https://acme.example', 'Berlin', 'DE', 'VERIFIED', :admin, :t, :rec)
                """).param("id", companyId).param("admin", adminId).param("t", ts).param("rec", recruiterId).update();
        jdbc.sql("INSERT INTO core.company_members (company_id, user_id, member_role) VALUES (:c, :u, 'OWNER')")
                .param("c", companyId).param("u", recruiterId).update();

        List<UUID> skillIds = SKILLS.stream().map(this::skill).toList();
        for (int i = 0; i < 60; i++) {
            String title = TITLES.get(i % TITLES.size()) + (i >= TITLES.size() ? " " + (i / TITLES.size() + 1) : "");
            String[] place = PLACES.get(i % PLACES.size());
            UUID jobId = UUID.randomUUID();
            int salaryMin = 40_000 + (i % 7) * 10_000;
            jdbc.sql("""
                    INSERT INTO core.jobs (id, company_id, created_by, title, slug, description, requirements, benefits,
                        employment_type, work_mode, experience_level, location_city, location_state, location_country,
                        salary_min, salary_max, salary_currency, salary_period, status, published_at, expires_at,
                        created_at, updated_at)
                    VALUES (:id, :company, :by, :title, :slug, :descr, 'Solid fundamentals and clear communication.',
                        'Learning budget and flexible hours.', :type, :mode, :level, :city, :state, :country,
                        :smin, :smax, 'USD', 'YEAR', 'PUBLISHED', :pub, :exp, :pub, :pub)
                    """)
                    .param("id", jobId).param("company", companyId).param("by", recruiterId).param("title", title)
                    .param("slug", slug(title) + "-" + (i + 1))
                    .param("descr", "Join Acme Labs as " + title + ". You will design, build and operate production services "
                            + "with a friendly team and modern tooling.")
                    .param("type", TYPES.get(i % TYPES.size())).param("mode", MODES.get(i % MODES.size()))
                    .param("level", LEVELS.get(i % LEVELS.size())).param("city", place[0]).param("state", place[1])
                    .param("country", place[2]).param("smin", salaryMin).param("smax", salaryMin + 30_000)
                    .param("pub", OffsetDateTime.ofInstant(now.minus(Duration.ofHours(i + 1)), ZoneOffset.UTC))
                    .param("exp", OffsetDateTime.ofInstant(now.plus(Duration.ofDays(30)), ZoneOffset.UTC)).update();
            for (int k = 0; k < 3; k++) {
                jdbc.sql("INSERT INTO core.job_skills (job_id, skill_id, is_required) VALUES (:j, :s, true) ON CONFLICT DO NOTHING")
                        .param("j", jobId).param("s", skillIds.get((i + k * 4) % skillIds.size())).update();
            }
        }
    }

    private UUID user(String hash, String email, String role, String first, String last, String handle, OffsetDateTime ts) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO core.users (id, email, password_hash, role, status, first_name, last_name, handle,
                                        email_verified_at, token_version, version, created_at, updated_at)
                VALUES (:id, :email, :hash, :role, 'ACTIVE', :first, :last, :handle, :t, 0, 0, :t, :t)
                """).param("id", id).param("email", email).param("hash", hash).param("role", role)
                .param("first", first).param("last", last).param("handle", handle).param("t", ts).update();
        return id;
    }

    private UUID skill(String name) {
        UUID existing = jdbc.sql("SELECT id FROM core.skills WHERE name = :n").param("n", name).query(UUID.class)
                .optional().orElse(null);
        if (existing != null) {
            return existing;
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO core.skills (id, name, slug, is_verified) VALUES (:id, :n, :slug, true)")
                .param("id", id).param("n", name).param("slug", slug(name)).update();
        return id;
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }
}
