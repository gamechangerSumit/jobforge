package com.jobforge.backend.community.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobforge.backend.community.app.CommunityRecords.FeedKind;
import com.jobforge.backend.community.app.CommunityViews.PostView;
import com.jobforge.backend.community.app.CommunityViews.ShareAck;
import com.jobforge.backend.community.app.InteractionService;
import com.jobforge.backend.community.app.PostService;
import com.jobforge.backend.community.app.PostService.CreatePostCommand;
import com.jobforge.backend.community.app.PostService.EditPostCommand;
import com.jobforge.backend.community.domain.PostType;
import com.jobforge.backend.community.domain.ShareChannel;
import com.jobforge.backend.shared.api.CursorResponse;
import com.jobforge.backend.shared.config.ApiPaths;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API_CONTRACT 12.9 posts, feed, saved, like/save/share. Every endpoint needs authentication (the global
 * "anyRequest authenticated" rule); ownership and visibility are enforced in the services (404 when not visible,
 * 403 when visible but not allowed). Writes use rate class WRITE_COMMUNITY (RequestRateLimitInterceptor).
 */
@RestController
@RequestMapping("/community")
public class PostController {

    public record CreatePostRequest(
            @NotNull PostType type,
            @Size(max = 2000) String title,
            @NotNull @Size(max = 40000) String body,
            @Size(max = 20) List<@Size(max = 100) String> tags,
            JsonNode typeMetadata) {}

    public record EditPostRequest(
            @Size(max = 2000) String title,
            @Size(max = 40000) String body,
            @Size(max = 20) List<@Size(max = 100) String> tags,
            JsonNode typeMetadata) {}

    public record ShareRequest(@NotNull ShareChannel channel) {}

    private final PostService posts;
    private final InteractionService interactions;

    public PostController(PostService posts, InteractionService interactions) {
        this.posts = posts;
        this.interactions = interactions;
    }

    @GetMapping("/posts")
    public CursorResponse<PostView> feed(@AuthenticationPrincipal AuthenticatedUser caller, HttpServletRequest request) {
        QueryParams p = new QueryParams(request, "feed", "type", "tag", "authorId", "q", "cursor", "limit");
        String feed = p.string("feed");
        FeedKind kind = feed == null ? FeedKind.LATEST : switch (feed) {
            case "latest" -> FeedKind.LATEST;
            case "following" -> FeedKind.FOLLOWING;
            case "trending" -> FeedKind.TRENDING;
            default -> throw com.jobforge.backend.shared.error.ValidationFailedException.of("feed", "INVALID_ENUM",
                    "must be one of latest, following, trending");
        };
        return posts.feed(caller, kind, p.enumValue("type", PostType.class), p.string("tag"), p.uuid("authorId"),
                p.string("q"), p.string("cursor"), CommunityParams.limit(p));
    }

    @GetMapping("/saved")
    public CursorResponse<PostView> saved(@AuthenticationPrincipal AuthenticatedUser caller, HttpServletRequest request) {
        QueryParams p = new QueryParams(request, "cursor", "limit");
        return posts.saved(caller, p.string("cursor"), CommunityParams.limit(p));
    }

    @PostMapping("/posts")
    public ResponseEntity<PostView> create(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CreatePostRequest body) {
        PostView created = posts.create(caller,
                new CreatePostCommand(body.type(), body.title(), body.body(), body.tags(), body.typeMetadata()));
        return ResponseEntity.created(URI.create(ApiPaths.BASE + "/community/posts/" + created.id())).body(created);
    }

    @GetMapping("/posts/{id}")
    public PostView get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return posts.get(caller, id);
    }

    @PatchMapping("/posts/{id}")
    public PostView edit(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody EditPostRequest body) {
        return posts.edit(caller, id, new EditPostCommand(body.title(), body.body(), body.tags(), body.typeMetadata()));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        posts.delete(caller, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/posts/{id}/like")
    public ResponseEntity<Void> like(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        interactions.likePost(caller, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{id}/like")
    public ResponseEntity<Void> unlike(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        interactions.unlikePost(caller, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/posts/{id}/save")
    public ResponseEntity<Void> save(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        interactions.savePost(caller, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{id}/save")
    public ResponseEntity<Void> unsave(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        interactions.unsavePost(caller, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{id}/share")
    public ShareAck share(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody ShareRequest body) {
        return interactions.share(caller, id, body.channel());
    }
}
