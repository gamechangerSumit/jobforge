package com.jobforge.backend.community.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.community.app.CommunityRecords.CommentRow;
import com.jobforge.backend.community.app.CommunityRecords.PostRow;
import com.jobforge.backend.community.app.CommunityViews.*;
import com.jobforge.backend.user.facade.UserSummaryFacade;
import com.jobforge.backend.user.facade.UserSummaryFacade.UserSummaryView;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds response DTOs for a page of rows with batched lookups (no N+1): authors, tags, viewer flags. */
@Component
public class ViewAssembler {

    private final CommunityRepository repository;
    private final UserSummaryFacade users;
    private final ObjectMapper mapper;

    public ViewAssembler(CommunityRepository repository, UserSummaryFacade users, ObjectMapper mapper) {
        this.repository = repository;
        this.users = users;
        this.mapper = mapper;
    }

    public List<PostView> posts(UUID viewerId, List<PostRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> postIds = rows.stream().map(PostRow::id).toList();
        List<UUID> authorIds = rows.stream().map(PostRow::authorId).distinct().toList();
        Map<UUID, UserSummaryView> authors = users.summaries(authorIds);
        Map<UUID, List<String>> tags = repository.tagSlugsOf(postIds);
        Set<UUID> liked = repository.likedPosts(viewerId, postIds);
        Set<UUID> saved = repository.savedPosts(viewerId, postIds);
        Set<UUID> following = repository.followedUsers(viewerId, authorIds);
        return rows.stream().map(r -> new PostView(r.id(), r.type().name(), r.title(), r.body(),
                tags.getOrDefault(r.id(), List.of()), json(r.typeMetadataJson()), author(authors.get(r.authorId()), r.authorId()),
                new Counts(r.likes(), r.comments(), r.saves(), r.shares()),
                new PostViewer(liked.contains(r.id()), saved.contains(r.id()), following.contains(r.authorId())),
                r.status().name(), r.createdAt(), r.editedAt())).toList();
    }

    public List<CommentView> comments(UUID viewerId, List<CommentRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(CommentRow::id).toList();
        Map<UUID, UserSummaryView> authors = users.summaries(rows.stream().map(CommentRow::authorId).distinct().toList());
        Map<UUID, Integer> replies = repository.replyCounts(ids);
        Set<UUID> liked = repository.likedComments(viewerId, ids);
        return rows.stream().map(r -> new CommentView(r.id(), r.postId(), r.parentId(), r.body(),
                author(authors.get(r.authorId()), r.authorId()),
                new CommentCounts(r.likes(), replies.getOrDefault(r.id(), 0)), new CommentViewer(liked.contains(r.id())),
                r.status().name(), r.createdAt(), r.editedAt())).toList();
    }

    static AuthorView author(UserSummaryView s, UUID fallbackId) {
        if (s == null) {
            return new AuthorView(fallbackId, "deleted", "Deleted", "User", null, null);
        }
        return new AuthorView(s.id(), s.handle(), s.firstName(), s.lastName(), s.avatarUrl(), s.role());
    }

    private JsonNode json(String raw) {
        try {
            return mapper.readTree(raw == null ? "{}" : raw);
        } catch (JsonProcessingException e) {
            return mapper.createObjectNode();
        }
    }

    static <T> Collection<T> nonNull(Collection<T> c) {
        return c == null ? List.of() : c;
    }
}
