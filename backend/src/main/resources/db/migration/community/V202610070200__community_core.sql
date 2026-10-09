-- DATABASE_SCHEMA 5 (schema community). References core only (community -> core direction).
-- The runner sets the default schema to community; core objects are always schema-qualified.

CREATE TABLE posts (
    id              uuid PRIMARY KEY,
    author_user_id  uuid          NOT NULL REFERENCES core.users (id),
    type            varchar(15)   NOT NULL,
    title           varchar(150),
    body            text          NOT NULL,
    type_metadata   jsonb         NOT NULL DEFAULT '{}'::jsonb,
    status          varchar(10)   NOT NULL DEFAULT 'ACTIVE',
    like_count      int           NOT NULL DEFAULT 0,
    comment_count   int           NOT NULL DEFAULT 0,
    save_count      int           NOT NULL DEFAULT 0,
    share_count     int           NOT NULL DEFAULT 0,
    edited_at       timestamptz,
    search_vector   tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', body), 'C')) STORED,
    version         bigint        NOT NULL DEFAULT 0,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    CONSTRAINT ck_posts_type CHECK (type IN ('DISCUSSION', 'HIRING', 'REFERRAL', 'PROJECT', 'CAREER_ADVICE', 'TECHNICAL', 'EVENT')),
    CONSTRAINT ck_posts_status CHECK (status IN ('ACTIVE', 'HIDDEN', 'REMOVED')),
    CONSTRAINT ck_posts_body CHECK (char_length(body) BETWEEN 1 AND 10000),
    CONSTRAINT ck_posts_counters CHECK (like_count >= 0 AND comment_count >= 0 AND save_count >= 0 AND share_count >= 0)
);
CREATE INDEX idx_posts_feed ON posts (created_at DESC, id DESC) WHERE status = 'ACTIVE' AND deleted_at IS NULL;
CREATE INDEX idx_posts_author ON posts (author_user_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_posts_type ON posts (type, created_at DESC) WHERE status = 'ACTIVE' AND deleted_at IS NULL;
CREATE INDEX idx_posts_search ON posts USING gin (search_vector);
CREATE TRIGGER trg_posts_updated_at BEFORE UPDATE ON posts
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

CREATE TABLE tags (
    id          uuid PRIMARY KEY,
    name        varchar(30)  NOT NULL,
    slug        varchar(30)  NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tags_slug UNIQUE (slug),
    CONSTRAINT ck_tags_slug CHECK (slug ~ '^[a-z0-9-]{2,30}$')
);

-- max 5 tags per post is enforced in the service
CREATE TABLE post_tags (
    post_id  uuid NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    tag_id   uuid NOT NULL REFERENCES tags (id),
    PRIMARY KEY (post_id, tag_id)
);
CREATE INDEX idx_post_tags_tag ON post_tags (tag_id);

CREATE TABLE comments (
    id                 uuid PRIMARY KEY,
    post_id            uuid          NOT NULL REFERENCES posts (id),
    author_user_id     uuid          NOT NULL REFERENCES core.users (id),
    parent_comment_id  uuid          REFERENCES comments (id),
    body               text          NOT NULL,
    status             varchar(10)   NOT NULL DEFAULT 'ACTIVE',
    like_count         int           NOT NULL DEFAULT 0,
    created_at         timestamptz   NOT NULL DEFAULT now(),
    updated_at         timestamptz   NOT NULL DEFAULT now(),
    edited_at          timestamptz,
    deleted_at         timestamptz,
    CONSTRAINT ck_comments_body CHECK (char_length(body) BETWEEN 1 AND 2000),
    CONSTRAINT ck_comments_status CHECK (status IN ('ACTIVE', 'HIDDEN', 'REMOVED')),
    CONSTRAINT ck_comments_like_count CHECK (like_count >= 0)
);
-- reply depth (max 2) is enforced in the service
CREATE INDEX idx_comments_post ON comments (post_id, created_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_comments_parent ON comments (parent_comment_id) WHERE parent_comment_id IS NOT NULL;
CREATE INDEX idx_comments_author ON comments (author_user_id);
CREATE TRIGGER trg_comments_updated_at BEFORE UPDATE ON comments
    FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();

CREATE TABLE post_likes (
    post_id     uuid         NOT NULL REFERENCES posts (id),
    user_id     uuid         NOT NULL REFERENCES core.users (id),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX idx_post_likes_user ON post_likes (user_id, created_at DESC);

CREATE TABLE comment_likes (
    comment_id  uuid         NOT NULL REFERENCES comments (id),
    user_id     uuid         NOT NULL REFERENCES core.users (id),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (comment_id, user_id)
);

CREATE TABLE post_saves (
    post_id     uuid         NOT NULL REFERENCES posts (id),
    user_id     uuid         NOT NULL REFERENCES core.users (id),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX idx_post_saves_user ON post_saves (user_id, created_at DESC);

CREATE TABLE post_shares (
    id          uuid PRIMARY KEY,
    post_id     uuid         NOT NULL REFERENCES posts (id),
    user_id     uuid         REFERENCES core.users (id),
    channel     varchar(10)  NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_post_shares_channel CHECK (channel IN ('COPY_LINK', 'LINKEDIN', 'X', 'EMAIL'))
);
CREATE INDEX idx_post_shares_post ON post_shares (post_id);

CREATE TABLE user_follows (
    follower_id  uuid         NOT NULL REFERENCES core.users (id),
    followee_id  uuid         NOT NULL REFERENCES core.users (id),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followee_id),
    CONSTRAINT ck_user_follows_self CHECK (follower_id <> followee_id)
);
CREATE INDEX idx_user_follows_followee ON user_follows (followee_id);

CREATE TABLE tag_follows (
    user_id     uuid         NOT NULL REFERENCES core.users (id),
    tag_id      uuid         NOT NULL REFERENCES tags (id),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, tag_id)
);
CREATE INDEX idx_tag_follows_tag ON tag_follows (tag_id);

CREATE TABLE company_follows (
    user_id     uuid         NOT NULL REFERENCES core.users (id),
    company_id  uuid         NOT NULL REFERENCES core.companies (id),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, company_id)
);
CREATE INDEX idx_company_follows_company ON company_follows (company_id);

CREATE TABLE mentions (
    id                 uuid PRIMARY KEY,
    source_type        varchar(10)  NOT NULL,
    source_id          uuid         NOT NULL,
    mentioned_user_id  uuid         NOT NULL REFERENCES core.users (id),
    created_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_mentions_source CHECK (source_type IN ('POST', 'COMMENT')),
    CONSTRAINT uq_mentions UNIQUE (source_type, source_id, mentioned_user_id)
);
CREATE INDEX idx_mentions_user ON mentions (mentioned_user_id, created_at DESC);
