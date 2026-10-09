-- JobForge Phase 1.5: Companies and Company Members

CREATE TABLE core.companies (
                                id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                                name                varchar(150) NOT NULL,
                                slug                varchar(160) NOT NULL,
                                description         text,
                                industry            varchar(80),
                                size_band           varchar(12),
                                website_url         varchar(255),
                                logo_key            varchar(255),
                                hq_city             varchar(80),
                                hq_state            varchar(80),
                                hq_country          char(2),
                                founded_year        smallint,
                                verification_status varchar(12) NOT NULL DEFAULT 'PENDING',
                                verified_by         uuid,
                                verified_at         timestamptz,
                                rejection_reason    varchar(500),
                                created_by          uuid NOT NULL,
                                version             bigint NOT NULL DEFAULT 0,
                                created_at          timestamptz NOT NULL DEFAULT now(),
                                updated_at          timestamptz NOT NULL DEFAULT now(),
                                deleted_at          timestamptz,

                                CONSTRAINT uq_companies_slug
                                    UNIQUE (slug),

                                CONSTRAINT ck_companies_size_band
                                    CHECK (
                                        size_band IS NULL
                                            OR size_band IN (
                                                             '1_10',
                                                             '11_50',
                                                             '51_200',
                                                             '201_500',
                                                             '501_1000',
                                                             '1000_PLUS'
                                            )
                                        ),

                                CONSTRAINT ck_companies_founded_year
                                    CHECK (
                                        founded_year IS NULL
                                            OR founded_year BETWEEN 1800 AND 2100
                                        ),

                                CONSTRAINT ck_companies_verification_status
                                    CHECK (
                                        verification_status IN (
                                                                'PENDING',
                                                                'VERIFIED',
                                                                'REJECTED',
                                                                'SUSPENDED'
                                            )
                                        ),

                                CONSTRAINT fk_companies_verified_by
                                    FOREIGN KEY (verified_by)
                                        REFERENCES core.users(id),

                                CONSTRAINT fk_companies_created_by
                                    FOREIGN KEY (created_by)
                                        REFERENCES core.users(id)
);


CREATE TABLE core.company_members (
                                      company_id uuid NOT NULL,
                                      user_id    uuid NOT NULL,
                                      member_role varchar(10) NOT NULL,
                                      created_at timestamptz NOT NULL DEFAULT now(),

                                      CONSTRAINT pk_company_members
                                          PRIMARY KEY (company_id, user_id),

                                      CONSTRAINT uq_company_members_user
                                          UNIQUE (user_id),

                                      CONSTRAINT fk_company_members_company
                                          FOREIGN KEY (company_id)
                                              REFERENCES core.companies(id),

                                      CONSTRAINT fk_company_members_user
                                          FOREIGN KEY (user_id)
                                              REFERENCES core.users(id),

                                      CONSTRAINT ck_company_members_role
                                          CHECK (
                                              member_role IN (
                                                              'OWNER',
                                                              'MEMBER'
                                                  )
                                              )
);


CREATE INDEX idx_companies_verification_status
    ON core.companies (verification_status);

CREATE INDEX idx_companies_name_trgm
    ON core.companies
    USING gin (name public.gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_company_members_user
    ON core.company_members (user_id);

CREATE INDEX idx_company_members_company
    ON core.company_members (company_id);


CREATE TRIGGER trg_companies_updated_at
    BEFORE UPDATE ON core.companies
    FOR EACH ROW
    EXECUTE FUNCTION core.set_updated_at();