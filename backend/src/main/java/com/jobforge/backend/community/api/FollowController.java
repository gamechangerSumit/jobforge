package com.jobforge.backend.community.api;

import com.jobforge.backend.community.app.CommunityViews.TagView;
import com.jobforge.backend.community.app.InteractionService;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT 12.9: follow users and tags, tag autocomplete and trending. All authenticated; set/unset are idempotent (204). */
@RestController
public class FollowController {

    private final InteractionService interactions;

    public FollowController(InteractionService interactions) {
        this.interactions = interactions;
    }

    @PutMapping("/users/{id}/follow")
    public ResponseEntity<Void> followUser(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        interactions.followUser(caller, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{id}/follow")
    public ResponseEntity<Void> unfollowUser(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        interactions.unfollowUser(caller, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/community/tags/{slug}/follow")
    public ResponseEntity<Void> followTag(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable String slug) {
        interactions.followTag(caller, slug);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/community/tags/{slug}/follow")
    public ResponseEntity<Void> unfollowTag(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable String slug) {
        interactions.unfollowTag(caller, slug);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/community/tags")
    public List<TagView> searchTags(HttpServletRequest request) {
        QueryParams p = new QueryParams(request, "q");
        return interactions.searchTags(p.string("q"));
    }

    @GetMapping("/community/tags/trending")
    public List<TagView> trendingTags() {
        return interactions.trendingTags();
    }
}
