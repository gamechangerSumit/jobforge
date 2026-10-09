package com.jobforge.backend.user.api;

import com.jobforge.backend.shared.web.QueryParams;
import com.jobforge.backend.user.app.UserDirectory.PublicCard;
import com.jobforge.backend.user.app.UserDirectory.UserSummary;
import com.jobforge.backend.user.app.UserDirectoryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT 12.1 - authenticated mention autocomplete and public cards (any signed-in role). */
@RestController
@RequestMapping("/users")
public class UserDirectoryController {

    private final UserDirectoryService directory;

    public UserDirectoryController(UserDirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping("/search")
    public List<UserSummary> search(HttpServletRequest request) {
        return directory.search(new QueryParams(request, "q").string("q"));
    }

    @GetMapping("/{id}/public")
    public PublicCard publicCard(@PathVariable UUID id) {
        return directory.publicCard(id);
    }
}
