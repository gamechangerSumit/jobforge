package com.jobforge.backend.profile.api;

import com.jobforge.backend.profile.app.SkillCatalogService;
import com.jobforge.backend.profile.facade.SkillCatalogFacade.SkillRef;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT §12.2 — public skill autocomplete ({@code GET /skills?q=&limit=}). */
@RestController
@RequestMapping("/skills")
public class SkillCatalogController {

    private final SkillCatalogService skills;

    public SkillCatalogController(SkillCatalogService skills) {
        this.skills = skills;
    }

    @GetMapping
    public List<SkillRef> search(@RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return skills.search(q, limit);
    }
}
