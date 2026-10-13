package com.jobforge.backend.community.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.community.app.CommunityEvents.PostCreated;
import com.jobforge.backend.community.app.CommunityEvents.UserMentioned;
import com.jobforge.backend.community.app.CommunityRecords.*;
import com.jobforge.backend.community.app.CommunityViews.PostView;
import com.jobforge.backend.community.domain.CommunityLimits;
import com.jobforge.backend.community.domain.MentionParser;
import com.jobforge.backend.community.domain.ModerationState;
import com.jobforge.backend.community.domain.PostType;
import com.jobforge.backend.community.domain.Tags;
import com.jobforge.backend.shared.api.CursorMeta;
import com.jobforge.backend.shared.api.CursorResponse;
import com.jobforge.backend.shared.audit.AuditAction;
import com.jobforge.backend.shared.audit.AuditEntry;
import com.jobforge.backend.shared.audit.AuditOutcome;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.FieldErrorDetail;
import com.jobforge.backend.shared.error.ForbiddenException;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.audit.AuditService;
import com.jobforge.backend.shared.events.DomainEvent;
import com.jobforge.backend.shared.events.EventPublisher;
import com.jobforge.backend.shared.events.EventTopics;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.user.facade.UserSummaryFacade;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Posts: create/read/edit/delete, feed and saved list (API_CONTRACT 12.9). Counters, audit and outbox share one tx. */
@Service
public class PostService {

    private static final String NOT_FOUND = PostAccess.NOT_FOUND;
    private static final int TRENDING_DAYS = 7;

    public record CreatePostCommand(PostType type, String title, String body, List<String> tags, JsonNode typeMetadata) {}

    /** {@code null} field = unchanged. A blank {@code title} clears the title. */
    public record EditPostCommand(String title, String body, List<String> tags, JsonNode typeMetadata) {}

    private final CommunityRepository repository;
    private final ViewAssembler views;
    private final PostAccess access;
    private final TypeMetadataValidator metadata;
    private final UserSummaryFacade users;
    private final AuditService audit;
    private final EventPublisher events;
    private final CursorCodec cursors;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SpamGuard spam;

    public PostService(CommunityRepository repository, ViewAssembler views, PostAccess access,
            TypeMetadataValidator metadata, UserSummaryFacade users, AuditService audit, EventPublisher events,
            CursorCodec cursors, ObjectMapper mapper, Clock clock, SpamGuard spam) {
        this.spam = spam;
        this.repository = repository;
        this.views = views;
        this.access = access;
        this.metadata = metadata;
        this.users = users;
        this.audit = audit;
        this.events = events;
        this.cursors = cursors;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public PostView create(AuthenticatedUser caller, CreatePostCommand command) {
        if (command.type() == null) {
            throw ValidationFailedException.of("type", "REQUIRED", "type is required");
        }
        String title = CommunityText.title(command.title());
        String body = CommunityText.body(command.body(), CommunityLimits.BODY_MAX, "body");
        Set<String> slugs = tags(command.tags());
        var meta = metadata.validate(command.type(), command.typeMetadata());
        spam.checkNewPost(caller, body);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        repository.insertPost(id, caller.id(), command.type(), title, body, meta.toString(), now);
        if (!slugs.isEmpty()) {
            repository.replacePostTags(id, repository.upsertTags(slugs, now).values());
        }
        syncMentions("POST", id, id, body, caller, now);
        events.publish(new DomainEvent(EventTopics.COMMUNITY, "PostCreated", "Post", id, caller.id(), caller.role(),
                new PostCreated(id, caller.id(), command.type().name())));
        return one(caller, id);
    }

    @Transactional(readOnly = true)
    public PostView get(AuthenticatedUser caller, UUID id) {
        PostRow post = repository.findPost(id).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        access.requireReadable(post, caller);
        return views.posts(caller.id(), List.of(post)).get(0);
    }

    @Transactional
    public PostView edit(AuthenticatedUser caller, UUID id, EditPostCommand command) {
        if (command.title() == null && command.body() == null && command.tags() == null
                && command.typeMetadata() == null) {
            throw ValidationFailedException.of("body", "REQUIRED", "at least one field must be provided");
        }
        PostRow post = repository.findPostForUpdate(id).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        access.requireReadable(post, caller);
        if (!post.authorId().equals(caller.id())) {
            throw new ForbiddenException("Only the author can edit this post.");
        }
        if (post.status() != ModerationState.ACTIVE) {
            throw new BusinessRuleException("This post is not editable.");
        }
        String title = command.title() == null ? post.title() : CommunityText.title(command.title());
        String body = command.body() == null ? post.body()
                : CommunityText.body(command.body(), CommunityLimits.BODY_MAX, "body");
        String metaJson = command.typeMetadata() == null ? post.typeMetadataJson()
                : metadata.validate(post.type(), command.typeMetadata()).toString();
        Set<String> slugs = command.tags() == null ? null : tags(command.tags());
        if (command.body() != null) {
            spam.checkEditedPost(caller, body);
        }
        Instant now = clock.instant();
        repository.updatePost(id, title, body, metaJson, now);
        if (slugs != null) {
            repository.replacePostTags(id, slugs.isEmpty() ? List.of() : repository.upsertTags(slugs, now).values());
        }
        if (command.body() != null) {
            syncMentions("POST", id, id, body, caller, now);
        }
        return one(caller, id);
    }

    @Transactional
    public void delete(AuthenticatedUser caller, UUID id) {
        PostRow post = repository.findPostForUpdate(id).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        access.requireReadable(post, caller);
        boolean author = post.authorId().equals(caller.id());
        boolean admin = caller.role() == UserRole.ADMIN;
        if (!author && !admin) {
            throw new ForbiddenException("Only the author or an administrator can delete this post.");
        }
        if (!repository.softDeletePost(id, clock.instant())) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        if (admin && !author) { // decision D2: administrator deletions are audited as CONTENT_MODERATED
            audit.record(new AuditEntry(AuditAction.CONTENT_MODERATED, "Post", id, caller.id(), caller.role(),
                    AuditOutcome.SUCCESS, null, null,
                    Map.of("action", "ADMIN_DELETE", "authorId", post.authorId().toString())));
        }
    }

    @Transactional(readOnly = true)
    public CursorResponse<PostView> feed(AuthenticatedUser caller, FeedKind feed, PostType type, String tag,
            UUID authorId, String q, String cursor, int limit) {
        String slug = null;
        if (tag != null) {
            slug = Tags.normalize(tag);
            if (slug == null) {
                throw ValidationFailedException.of("tag", "FORMAT", "must be 2-30 characters of a-z, 0-9 or -");
            }
        }
        String query = q == null || q.isBlank() ? null : q.strip();
        if (query != null && (query.length() < 2 || query.length() > 100)) {
            throw ValidationFailedException.of("q", "SIZE", "must be between 2 and 100 characters");
        }
        String kind = "feed:" + feed;
        CursorPos after = cursors.decode(kind, cursor);
        List<PostRow> rows = repository.feed(new FeedFilter(feed, type, slug, authorId, query), caller.id(), after,
                limit + 1, clock.instant().minus(TRENDING_DAYS, ChronoUnit.DAYS));
        return page(caller, rows, limit, kind, feed == FeedKind.TRENDING);
    }

    @Transactional(readOnly = true)
    public CursorResponse<PostView> saved(AuthenticatedUser caller, String cursor, int limit) {
        CursorPos after = cursors.decode("saved", cursor);
        return page(caller, repository.saved(caller.id(), after, limit + 1), limit, "saved", false);
    }

    private CursorResponse<PostView> page(AuthenticatedUser caller, List<PostRow> rows, int limit, String kind,
            boolean scored) {
        boolean hasNext = rows.size() > limit;
        List<PostRow> pageRows = hasNext ? rows.subList(0, limit) : rows;
        String next = null;
        if (hasNext) {
            PostRow last = pageRows.get(pageRows.size() - 1);
            next = cursors.encode(kind, new CursorPos(last.keyTs(), scored ? last.score() : null, last.id()));
        }
        return new CursorResponse<>(views.posts(caller.id(), pageRows), new CursorMeta(next, hasNext, limit));
    }

    private PostView one(AuthenticatedUser caller, UUID id) {
        return views.posts(caller.id(), List.of(repository.findPost(id).orElseThrow())).get(0);
    }

    private static Set<String> tags(List<String> raw) {
        List<String> invalid = new ArrayList<>();
        Set<String> slugs = Tags.normalizeAll(raw, invalid);
        if (!invalid.isEmpty()) {
            throw new ValidationFailedException(List.of(new FieldErrorDetail("tags", "FORMAT",
                    "each tag must be 2-30 characters of a-z, 0-9 or -")));
        }
        if (slugs.size() > CommunityLimits.TAGS_MAX) {
            throw ValidationFailedException.of("tags", "SIZE", "at most " + CommunityLimits.TAGS_MAX + " tags");
        }
        return slugs;
    }

    /** Resolves @handles, stores new mentions and publishes UserMentioned for users mentioned for the first time. */
    void syncMentions(String sourceType, UUID sourceId, UUID postId, String text, AuthenticatedUser author, Instant now) {
        Set<UUID> targets = new HashSet<>(users.activeUserIdsByHandle(MentionParser.handles(text)).values());
        targets.remove(author.id()); // no self-mention notifications
        Set<UUID> fresh = repository.insertMentions(sourceType, sourceId, targets, now);
        repository.deleteMentionsExcept(sourceType, sourceId, targets);
        for (UUID userId : fresh) {
            events.publish(new DomainEvent(EventTopics.COMMUNITY, "UserMentioned", sourceType.equals("POST") ? "Post" : "Comment",
                    sourceId, author.id(), author.role(), new UserMentioned(sourceType, sourceId, postId, userId, author.id())));
        }
    }
}
