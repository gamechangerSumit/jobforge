-- JobForge Phase 1 / JS-3: Resumes

CREATE TABLE core.resumes (
                              id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                              seeker_profile_id   uuid NOT NULL,
                              original_filename   varchar(255) NOT NULL,
                              storage_key         varchar(255) NOT NULL,
                              content_type        varchar(100) NOT NULL,
                              size_bytes           integer NOT NULL,
                              sha256              char(64) NOT NULL,
                              is_primary          boolean NOT NULL DEFAULT false,
                              created_at          timestamptz NOT NULL DEFAULT now(),
                              deleted_at          timestamptz,

                              CONSTRAINT uq_resumes_storage_key
                                  UNIQUE (storage_key),

                              CONSTRAINT ck_resumes_content_type
                                  CHECK (content_type IN ('application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document')),

                              CONSTRAINT ck_resumes_size_bytes
                                  CHECK (size_bytes <= 5242880),

                              CONSTRAINT fk_resumes_seeker_profile
                                  FOREIGN KEY (seeker_profile_id)
                                      REFERENCES core.seeker_profiles(id)
);

CREATE INDEX idx_resumes_seeker_profile
    ON core.resumes (seeker_profile_id);

CREATE UNIQUE INDEX uq_resumes_primary_active
    ON core.resumes (seeker_profile_id)
    WHERE is_primary = true
      AND deleted_at IS NULL;