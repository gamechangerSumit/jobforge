package com.jobforge.backend.community.api;

import com.jobforge.backend.community.app.CommentService;
import com.jobforge.backend.community.app.CommunityViews.CommentView;
import com.jobforge.backend.community.app.InteractionService;
import com.jobforge.backend.shared.api.CursorResponse;
import com.jobforge.backend.shared.config.ApiPaths;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
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

/** API_CONTRACT 12.9 comments: list (top-level or replies via parentId), create, edit, delete, like. */
@RestController
@RequestMapping("/community")
public class CommentController {

    public record CreateCommentRequest(@NotNull @Size(max = 8000) String body, UUID parentId) {}

    public record EditCommentRequest(@NotNull @Size(max = 8000) String body) {}

    private final CommentService comments;
    private final InteractionService interactions;

    public CommentController(CommentService comments, InteractionService interactions) {
        this.comments = comments;
        this.interactions = interactions;
    }

    @GetMapping("/posts/{postId}/comments")
    public CursorResponse<CommentView> list(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID postId,
            HttpServletRequest request) {
        QueryParams p = new QueryParams(request, "parentId", "cursor", "limit");
        return comments.list(caller, postId, p.uuid("parentId"), p.string("cursor"), CommunityParams.limit(p));
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentView> create(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest body) {
        CommentView created = comments.create(caller, postId, body.body(), body.parentId());
        return ResponseEntity.created(URI.create(ApiPaths.BASE + "/community/comments/" + created.id())).body(created);
    }

    @PatchMapping("/comments/{id}")
    public CommentView edit(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody EditCommentRequest body) {
        return comments.edit(caller, id, body.body());
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        comments.delete(caller, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/comments/{id}/like")
    public ResponseEntity<Void> like(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        interactions.likeComment(caller, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/comments/{id}/like")
    public ResponseEntity<Void> unlike(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        interactions.unlikeComment(caller, id);
        return ResponseEntity.noContent().build();
    }
}
